/*
 * AttackDLC Setup — нативный установщик на C++ (Win32, статическая линковка /MT).
 *
 * Не требует .NET и VC++ Redistributable: запускается на чистом Windows-ПК.
 * Что делает:
 *   1. качает AttackLauncher-<ver>.zip с сервера релизов (WinHTTP, HTTPS, редиректы);
 *   2. распаковывает архив (собственный ZIP-разбор + puff/DEFLATE, public domain);
 *   3. подменяет app/, runtime/ и AttackLauncher.exe в %LOCALAPPDATA%\AttackLauncher,
 *      не трогая папку config (данные аккаунта/настройки);
 *   4. создаёт ярлыки на рабочем столе и в меню «Пуск»;
 *   5. запускает лаунчер.
 *
 * Переменные окружения (для тестов):
 *   ATK_SETUP_URL — подмена ссылки на архив;
 *   ATK_SETUP_DIR — подмена папки установки.
 */
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <winhttp.h>
#include <shellapi.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <tlhelp32.h>
#include <commctrl.h>

#include <string>
#include <vector>

extern "C" {
#include "puff.h"
}

#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "uuid.lib")
#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")

/* ------------------------- конфигурация ------------------------- */

static const wchar_t* kZipUrl =
    L"https://github.com/1kitikat1/Launch/releases/download/v1.7.3/AttackLauncher-1.7.3.zip";
static const wchar_t* kExeName = L"AttackLauncher.exe";

#define IDC_TITLE  1001
#define IDC_STATUS 1002
#define IDC_BAR    1003
#define IDC_BTN    1004
#define IDC_HINT   1005

#define WM_ST_TEXT (WM_APP + 1)  /* lParam = const wchar_t*  */
#define WM_ST_PROG (WM_APP + 2)  /* lParam = проценты 0..100 */
#define WM_ST_DONE (WM_APP + 3)  /* wParam = 0 ок, 1 ошибка */

/* --------------------------- состояние -------------------------- */

static HWND g_status = NULL, g_bar = NULL, g_btn = NULL, g_hint = NULL;
static HFONT g_font = NULL, g_titleFont = NULL, g_smallFont = NULL;
static HBRUSH g_bg = NULL;
static std::wstring g_dir, g_zip, g_err;
static bool g_busy = false, g_ready = false;

static std::wstring envOr(const wchar_t* name, const std::wstring& defv) {
  wchar_t buf[4096];
  DWORD n = GetEnvironmentVariableW(name, buf, 4096);
  return (n > 0 && n < 4096) ? std::wstring(buf, n) : defv;
}

static HWND g_wnd = NULL;

static void postText(const wchar_t* t) {
  if (g_wnd) PostMessageW(g_wnd, WM_ST_TEXT, 0, (LPARAM)t);
}

static void postProg(int p) {
  if (g_wnd) PostMessageW(g_wnd, WM_ST_PROG, 0, (LPARAM)p);
}

static bool hasSwitch(const wchar_t* s) {
  return wcsstr(GetCommandLineW(), s) != NULL;
}

/* ------------------------- файловые хелперы --------------------- */

static void removeTree(const std::wstring& path) {
  WIN32_FIND_DATAW fd;
  HANDLE h = FindFirstFileW((path + L"\\*").c_str(), &fd);
  if (h != INVALID_HANDLE_VALUE) {
    do {
      if (lstrcmpW(fd.cFileName, L".") == 0 || lstrcmpW(fd.cFileName, L"..") == 0) continue;
      std::wstring child = path + L"\\" + fd.cFileName;
      if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) removeTree(child);
      else DeleteFileW(child.c_str());
    } while (FindNextFileW(h, &fd));
    FindClose(h);
  }
  RemoveDirectoryW(path.c_str());
}

static void ensureDir(const std::wstring& dir) {
  for (size_t i = 3; i < dir.size(); ++i) {
    if (dir[i] == L'\\' || dir[i] == L'/') {
      std::wstring part = dir.substr(0, i);
      CreateDirectoryW(part.c_str(), NULL);
    }
  }
  CreateDirectoryW(dir.c_str(), NULL);
}

static bool fileExists(const std::wstring& p) {
  DWORD a = GetFileAttributesW(p.c_str());
  return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

static bool dirExists(const std::wstring& p) {
  DWORD a = GetFileAttributesW(p.c_str());
  return a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY);
}

static bool launcherRunning() {
  HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (snap == INVALID_HANDLE_VALUE) return false;
  PROCESSENTRY32W pe;
  pe.dwSize = sizeof(pe);
  bool found = false;
  if (Process32FirstW(snap, &pe)) {
    do {
      if (lstrcmpiW(pe.szExeFile, kExeName) == 0) { found = true; break; }
    } while (Process32NextW(snap, &pe));
  }
  CloseHandle(snap);
  return found;
}

/* --------------------------- скачивание -------------------------- */

static bool download(const std::wstring& url, const std::wstring& outPath,
                     unsigned long long* totalOut, std::wstring& err) {
  /* канонический приём: WinHttpCrackUrl возвращает указатели внутрь
     изменяемой копии строки, буфер обязан жить столько же, сколько uc */
  std::vector<wchar_t> ubuf(url.begin(), url.end());
  ubuf.push_back(0);
  URL_COMPONENTS uc;
  ZeroMemory(&uc, sizeof(uc));
  uc.dwStructSize = sizeof(uc);
  uc.dwSchemeLength = (DWORD)-1;
  uc.dwHostNameLength = (DWORD)-1;
  uc.dwUrlPathLength = (DWORD)-1;
  uc.dwExtraInfoLength = (DWORD)-1;
  if (!WinHttpCrackUrl(&ubuf[0], 0, 0, &uc)) {
    err = L"Не удалось разобрать ссылку на файл.";
    return false;
  }
  /* WinHttpCrackUrl не гарантирует завершающий '\0' — длину берём явно */
  std::wstring host = (uc.lpszHostName && uc.dwHostNameLength)
                          ? std::wstring(uc.lpszHostName, uc.dwHostNameLength)
                          : std::wstring();
  std::wstring object;
  if (uc.lpszUrlPath && uc.dwUrlPathLength)
    object.assign(uc.lpszUrlPath, uc.dwUrlPathLength);
  if (object.empty()) object = L"/";
  if (uc.lpszExtraInfo && uc.dwExtraInfoLength)
    object.append(uc.lpszExtraInfo, uc.dwExtraInfoLength);
  if (host.empty()) {
    err = L"В ссылке нет имени сервера.";
    return false;
  }

  HINTERNET ses = WinHttpOpen(L"AttackDLC-Setup/1.0",
                              WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                              WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
  if (!ses) { err = L"WinHttpOpen не сработал."; return false; }
  WinHttpSetTimeouts(ses, 20000, 20000, 60000, 60000);

  HINTERNET con = WinHttpConnect(ses, host.c_str(), uc.nPort, 0);
  if (!con) {
    err = L"Нет соединения с " + host + L":" + std::to_wstring(uc.nPort) +
          L" (WinHTTP ошибка " + std::to_wstring(GetLastError()) + L").";
    WinHttpCloseHandle(ses);
    return false;
  }

  DWORD flags = (uc.nScheme == INTERNET_SCHEME_HTTPS) ? WINHTTP_FLAG_SECURE : 0;
  HINTERNET req = WinHttpOpenRequest(con, L"GET", object.c_str(), NULL,
                                     WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags);
  if (!req) { err = L"Не удалось открыть запрос."; WinHttpCloseHandle(con); WinHttpCloseHandle(ses); return false; }

  bool ok = false;
  do {
    if (!WinHttpSendRequest(req, WINHTTP_NO_ADDITIONAL_HEADERS, 0,
                            WINHTTP_NO_REQUEST_DATA, 0, 0, 0)) {
      err = L"Не удалось выполнить запрос (проверьте интернет)."; break;
    }
    if (!WinHttpReceiveResponse(req, NULL)) {
      err = L"Сервер не ответил."; break;
    }
    DWORD status = 0, sz = sizeof(status);
    if (!WinHttpQueryHeaders(req, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                             WINHTTP_HEADER_NAME_BY_INDEX, &status, &sz, WINHTTP_NO_HEADER_INDEX)) {
      err = L"Не удалось прочитать ответ сервера."; break;
    }
    if (status != 200) {
      err = L"Сервер ответил кодом " + std::to_wstring(status) +
            L". Файл ещё не опубликован?";
      break;
    }
    DWORD len = 0, ls = sizeof(len);
    WinHttpQueryHeaders(req, WINHTTP_QUERY_CONTENT_LENGTH | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &len, &ls, WINHTTP_NO_HEADER_INDEX);
    unsigned long long total = len;
    if (totalOut) *totalOut = total;

    HANDLE hf = CreateFileW(outPath.c_str(), GENERIC_WRITE, 0, NULL,
                            CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (hf == INVALID_HANDLE_VALUE) { err = L"Не удалось создать файл во временной папке."; break; }

    unsigned char buf[64 * 1024];
    unsigned long long got = 0;
    DWORD avail = 0, wr = 0;
    bool readOk = true;
    while (WinHttpQueryDataAvailable(req, &avail) && avail > 0) {
      DWORD need = avail;
      while (need > 0) {
        DWORD chunk = need > sizeof(buf) ? (DWORD)sizeof(buf) : need;
        DWORD gotNow = 0;
        if (!WinHttpReadData(req, buf, chunk, &gotNow) || gotNow == 0) { readOk = false; break; }
        if (!WriteFile(hf, buf, gotNow, &wr, NULL)) { readOk = false; break; }
        got += gotNow;
        need -= gotNow;
      }
      if (!readOk) break;
      if (total > 0) {
        int p = (int)(got * 85ull / total);
        if (p > 85) p = 85;
        postProg(p);
      }
    }
    CloseHandle(hf);
    if (!readOk) { err = L"Скачивание прервано — проверьте соединение."; break; }
    if (total > 0 && got != total) { err = L"Файл скачан не полностью."; break; }
    postProg(85);
    ok = true;
  } while (0);

  WinHttpCloseHandle(req);
  WinHttpCloseHandle(con);
  WinHttpCloseHandle(ses);
  if (!ok) DeleteFileW(outPath.c_str());
  return ok;
}

/* --------------------------- распаковка -------------------------- */

static unsigned int rd32(const std::vector<unsigned char>& z, size_t i) {
  return (unsigned int)z[i] | ((unsigned int)z[i + 1] << 8) |
         ((unsigned int)z[i + 2] << 16) | ((unsigned int)z[i + 3] << 24);
}
static unsigned int rd16(const std::vector<unsigned char>& z, size_t i) {
  return (unsigned int)z[i] | ((unsigned int)z[i + 1] << 8);
}

static std::wstring toWide(const std::string& s) {
  if (s.empty()) return std::wstring();
  int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), NULL, 0);
  if (n <= 0) return std::wstring();
  std::wstring w((size_t)n, 0);
  MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
  return w;
}

static bool extractZip(const std::wstring& zipPath, const std::wstring& destDir,
                       std::wstring& err) {
  HANDLE hf = CreateFileW(zipPath.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL,
                          OPEN_EXISTING, 0, NULL);
  if (hf == INVALID_HANDLE_VALUE) { err = L"Архив не открылся."; return false; }
  LARGE_INTEGER li;
  if (!GetFileSizeEx(hf, &li) || li.QuadPart <= 22) {
    CloseHandle(hf); err = L"Архив пустой."; return false;
  }
  size_t fsize = (size_t)li.QuadPart;
  std::vector<unsigned char> z(fsize);
  DWORD rd = 0;
  size_t off = 0;
  while (off < fsize) {
    DWORD want = (DWORD)((fsize - off) > (1u << 30) ? (1u << 30) : (fsize - off));
    if (!ReadFile(hf, &z[off], want, &rd, NULL) || rd == 0) break;
    off += rd;
  }
  CloseHandle(hf);
  if (off != fsize) { err = L"Не удалось прочитать архив целиком."; return false; }

  /* End Of Central Directory */
  long long i = (long long)fsize - 22;
  long long start = (long long)fsize - 65557;
  if (start < 0) start = 0;
  size_t eocd = 0;
  bool found = false;
  for (; i >= start; --i) {
    if (rd32(z, (size_t)i) == 0x06054b50u) { eocd = (size_t)i; found = true; break; }
  }
  if (!found) { err = L"Файл не похож на ZIP-архив."; return false; }

  unsigned int entries = rd16(z, eocd + 10);
  unsigned int cdSize = rd32(z, eocd + 12);
  unsigned int cdOff = rd32(z, eocd + 16);
  if ((size_t)cdOff + (size_t)cdSize > fsize) { err = L"Архив повреждён."; return false; }

  ensureDir(destDir);

  size_t p = cdOff, end = (size_t)cdOff + cdSize;
  unsigned int doneCount = 0;
  while (p + 46 <= end && rd32(z, p) == 0x02014b50u) {
    unsigned int method = rd16(z, p + 10);
    unsigned int csize = rd32(z, p + 20);
    unsigned int usize = rd32(z, p + 24);
    unsigned int nameLen = rd16(z, p + 28);
    unsigned int extraLen = rd16(z, p + 30);
    unsigned int cmtLen = rd16(z, p + 32);
    unsigned int localOff = rd32(z, p + 42);
    if (p + 46 + nameLen > fsize) { err = L"Архив повреждён (имя файла)."; return false; }

    std::string name((const char*)&z[p + 46], nameLen);
    p += 46 + nameLen + extraLen + cmtLen;
    ++doneCount;

    if (name.empty() || name[0] == '/' || name[0] == '\\') continue;
    if (name.find("..") != std::string::npos) continue;   /* zip-slip */

    for (size_t k = 0; k < name.size(); ++k)
      if (name[k] == '\\') name[k] = '/';

    std::wstring rel = toWide(name);
    if (rel.empty()) continue;

    bool isDir = !rel.empty() && (rel[rel.size() - 1] == L'/' || rel[rel.size() - 1] == L'\\');
    if (isDir) {
      if (rel[rel.size() - 1] == L'/' || rel[rel.size() - 1] == L'\\') rel.erase(rel.size() - 1);
      ensureDir(destDir + L"\\" + rel);
      continue;
    }

    if ((size_t)localOff + 30 > fsize || rd32(z, localOff) != 0x04034b50u) {
      err = L"Архив повреждён (заголовок файла)."; return false;
    }
    unsigned int lNameLen = rd16(z, localOff + 26);
    unsigned int lExtraLen = rd16(z, localOff + 28);
    size_t dataOff = (size_t)localOff + 30 + lNameLen + lExtraLen;
    if (dataOff + csize > fsize) { err = L"Архив повреждён (данные файла)."; return false; }

    std::wstring full = destDir + L"\\" + rel;
    std::wstring parent = full;
    size_t slash = parent.find_last_of(L"\\/");
    if (slash != std::wstring::npos) ensureDir(parent.substr(0, slash));

    HANDLE out = CreateFileW(full.c_str(), GENERIC_WRITE, 0, NULL,
                             CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) {
      err = L"Не удалось записать файл: " + rel;
      return false;
    }

    bool writeOk = true;
    if (method == 0) {
      DWORD wr = 0;
      if (csize > 0 && !WriteFile(out, &z[dataOff], csize, &wr, NULL)) writeOk = false;
    } else if (method == 8) {
      std::vector<unsigned char> dst(usize ? usize : 1);
      unsigned long dlen = (unsigned long)usize;
      unsigned long slen = (unsigned long)csize;
      int r = puff(dst.empty() ? NULL : &dst[0], &dlen, &z[dataOff], &slen);
      if (r != 0 || dlen != (unsigned long)usize) writeOk = false;
      else if (usize > 0) {
        DWORD wr = 0;
        if (!WriteFile(out, &dst[0], usize, &wr, NULL)) writeOk = false;
      }
    } else {
      writeOk = false;
    }
    CloseHandle(out);
    if (!writeOk) {
      err = L"Не удалось распаковать файл: " + rel;
      return false;
    }

    if (entries > 0) {
      int prog = 85 + (int)((unsigned long long)doneCount * 15ull / entries);
      if (prog > 100) prog = 100;
      postProg(prog);
    }
  }
  postProg(100);
  return true;
}

/* ---------------------------- ярлыки ----------------------------- */

static bool makeShortcut(const std::wstring& linkPath, const std::wstring& target,
                         const std::wstring& workDir) {
  IShellLinkW* psl = NULL;
  HRESULT hr = CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER,
                                IID_IShellLinkW, (void**)&psl);
  if (FAILED(hr) || !psl) return false;
  psl->SetPath(target.c_str());
  psl->SetWorkingDirectory(workDir.c_str());
  psl->SetDescription(L"AttackDLC — клиент для Minecraft");
  psl->SetIconLocation(target.c_str(), 0);

  IPersistFile* ppf = NULL;
  psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
  bool ok = false;
  if (ppf) {
    ok = SUCCEEDED(ppf->Save(linkPath.c_str(), TRUE));
    ppf->Release();
  }
  psl->Release();
  return ok;
}

static void createShortcuts(const std::wstring& dir, const std::wstring& exe) {
  wchar_t buf[MAX_PATH];
  if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_DESKTOPDIRECTORY, NULL, SHGFP_TYPE_CURRENT, buf)))
    makeShortcut(std::wstring(buf) + L"\\AttackDLC.lnk", exe, dir);
  if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_PROGRAMS, NULL, SHGFP_TYPE_CURRENT, buf))) {
    std::wstring folder = std::wstring(buf) + L"\\AttackDLC";
    CreateDirectoryW(folder.c_str(), NULL);
    makeShortcut(folder + L"\\AttackDLC.lnk", exe, dir);
  }
}

/* ------------------------- ход установки ------------------------- */

static bool runInstall(std::wstring& err) {
  if (launcherRunning()) {
    err = L"Лаунчер сейчас запущен — закройте его и нажмите «Повторить».";
    return false;
  }

  std::wstring url = envOr(L"ATK_SETUP_URL", kZipUrl);
  std::wstring tmpDir = envOr(L"TMP", L"C:\\Windows\\Temp");
  wchar_t uniq[64];
  wsprintfW(uniq, L"atk-setup-%u-%u.zip", GetCurrentProcessId(), GetTickCount());
  g_zip = tmpDir + L"\\" + uniq;

  postText(L"Скачиваем архив…");
  postProg(0);
  if (!download(url, g_zip, NULL, err)) return false;

  postText(L"Распаковываем файлы…");
  std::wstring staging = g_dir + L".new";
  removeTree(staging);
  if (!extractZip(g_zip, staging, err)) { removeTree(staging); return false; }

  std::wstring newExe = staging + L"\\" + kExeName;
  if (!fileExists(newExe)) {
    err = L"В архиве нет AttackLauncher.exe — файл неполный.";
    removeTree(staging);
    return false;
  }

  /* подменяем программные папки, config (данные аккаунта) не трогаем */
  removeTree(g_dir + L"\\app");
  removeTree(g_dir + L"\\runtime");
  DeleteFileW((g_dir + L"\\" + kExeName).c_str());

  ensureDir(g_dir);
  WIN32_FIND_DATAW fd;
  HANDLE h = FindFirstFileW((staging + L"\\*").c_str(), &fd);
  if (h != INVALID_HANDLE_VALUE) {
    do {
      if (lstrcmpW(fd.cFileName, L".") == 0 || lstrcmpW(fd.cFileName, L"..") == 0) continue;
      std::wstring src = staging + L"\\" + fd.cFileName;
      std::wstring dst = g_dir + L"\\" + fd.cFileName;
      if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
        MoveFileExW(src.c_str(), dst.c_str(), MOVEFILE_REPLACE_EXISTING);
      } else {
        MoveFileExW(src.c_str(), dst.c_str(), MOVEFILE_REPLACE_EXISTING);
      }
    } while (FindNextFileW(h, &fd));
    FindClose(h);
  }
  removeTree(staging);
  DeleteFileW(g_zip.c_str());

  std::wstring exe = g_dir + L"\\" + kExeName;
  if (!fileExists(exe)) {
    err = L"Установка не завершилась — файл лаунчера не найден.";
    return false;
  }

  postText(L"Создаём ярлыки…");
  if (!hasSwitch(L"--no-shortcuts")) {
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    createShortcuts(g_dir, exe);
    CoUninitialize();
  }

  postText(L"Готово! Лаунчер установлен.");
  postProg(100);
  return true;
}

static DWORD WINAPI worker(LPVOID) {
  g_err.clear();
  bool ok = runInstall(g_err);
  PostMessageW(g_wnd, WM_ST_DONE, ok ? 0 : 1, 0);
  return 0;
}

/* ------------------------------ UI ------------------------------ */

static HFONT makeFont(int h, int weight) {
  return CreateFontW(-h, 0, 0, 0, weight, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                     OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                     DEFAULT_PITCH, L"Segoe UI");
}

static void centerWindow(HWND wnd, int w, int h) {
  int sx = GetSystemMetrics(SM_CXSCREEN), sy = GetSystemMetrics(SM_CYSCREEN);
  SetWindowPos(wnd, NULL, (sx - w) / 2, (sy - h) / 2, 0, 0,
               SWP_NOSIZE | SWP_NOZORDER);
}

static LRESULT CALLBACK wndProc(HWND wnd, UINT msg, WPARAM wp, LPARAM lp) {
  switch (msg) {
    case WM_CREATE: {
      g_wnd = wnd;
      g_bg = CreateSolidBrush(RGB(12, 12, 12));
      g_font = makeFont(15, FW_NORMAL);
      g_titleFont = makeFont(24, FW_BOLD);
      g_smallFont = makeFont(12, FW_NORMAL);

      RECT rc; GetClientRect(wnd, &rc);
      int W = rc.right;

      HWND title = CreateWindowExW(0, L"STATIC", L"Установка AttackDLC",
                                   WS_CHILD | WS_VISIBLE,
                                   28, 22, W - 56, 34, wnd, (HMENU)IDC_TITLE, NULL, NULL);
      SendMessageW(title, WM_SETFONT, (WPARAM)g_titleFont, TRUE);

      g_status = CreateWindowExW(0, L"STATIC", L"Нажмите «Установить», чтобы начать.",
                                 WS_CHILD | WS_VISIBLE,
                                 28, 66, W - 56, 24, wnd, (HMENU)IDC_STATUS, NULL, NULL);
      SendMessageW(g_status, WM_SETFONT, (WPARAM)g_font, TRUE);

      g_bar = CreateWindowExW(0, PROGRESS_CLASSW, L"",
                              WS_CHILD | WS_VISIBLE | PBS_SMOOTH,
                              28, 100, W - 56, 20, wnd, (HMENU)IDC_BAR, NULL, NULL);
      SendMessageW(g_bar, PBM_SETRANGE32, 0, 100);
      SendMessageW(g_bar, PBM_SETPOS, 0, 0);
      SendMessageW(g_bar, PBM_SETBKCOLOR, 0, (LPARAM)RGB(38, 38, 38));
      SendMessageW(g_bar, PBM_SETBARCOLOR, 0, (LPARAM)RGB(255, 255, 255));

      g_btn = CreateWindowExW(0, L"BUTTON", L"Установить",
                              WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_OWNERDRAW,
                              28, 138, 190, 40, wnd, (HMENU)IDC_BTN, NULL, NULL);
      SendMessageW(g_btn, WM_SETFONT, (WPARAM)g_font, TRUE);

      g_hint = CreateWindowExW(0, L"STATIC", (L"Папка: " + g_dir).c_str(),
                               WS_CHILD | WS_VISIBLE,
                               28, 190, W - 56, 20, wnd, (HMENU)IDC_HINT, NULL, NULL);
      SendMessageW(g_hint, WM_SETFONT, (WPARAM)g_smallFont, TRUE);

      return 0;
    }

    case WM_CTLCOLORSTATIC: {
      HDC hdc = (HDC)wp;
      SetTextColor(hdc, RGB(232, 232, 232));
      SetBkColor(hdc, RGB(12, 12, 12));
      return (LRESULT)g_bg;
    }
    case WM_CTLCOLORBTN:
      return (LRESULT)g_bg;
    case WM_ERASEBKGND: {
      RECT rc; GetClientRect(wnd, &rc);
      FillRect((HDC)wp, &rc, g_bg);
      return 1;
    }

    case WM_DRAWITEM: {
      DRAWITEMSTRUCT* ds = (DRAWITEMSTRUCT*)lp;
      if (ds->CtlID == IDC_BTN) {
        bool pressed = (ds->itemState & ODS_SELECTED) != 0;
        bool dis = (ds->itemState & ODS_DISABLED) != 0;
        RECT rc = ds->rcItem;
        HBRUSH br = CreateSolidBrush(dis ? RGB(70, 70, 70)
                                     : (pressed ? RGB(205, 205, 205) : RGB(255, 255, 255)));
        FillRect(ds->hDC, &rc, br);
        DeleteObject(br);
        FrameRect(ds->hDC, &rc, (HBRUSH)GetStockObject(BLACK_BRUSH));
        SetBkMode(ds->hDC, TRANSPARENT);
        SetTextColor(ds->hDC, dis ? RGB(160, 160, 160) : RGB(0, 0, 0));
        wchar_t text[64];
        GetWindowTextW(ds->hwndItem, text, 64);
        DrawTextW(ds->hDC, text, -1, &rc, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        return TRUE;
      }
      return FALSE;
    }

    case WM_COMMAND:
      if (LOWORD(wp) == IDC_BTN && HIWORD(wp) == BN_CLICKED) {
        if (g_busy) return 0;
        if (g_ready) {
          std::wstring exe = g_dir + L"\\" + kExeName;
          ShellExecuteW(NULL, L"open", exe.c_str(), NULL, g_dir.c_str(), SW_SHOWNORMAL);
          PostMessageW(wnd, WM_CLOSE, 0, 0);
          return 0;
        }
        g_busy = true;
        EnableWindow(g_btn, FALSE);
        SetWindowTextW(g_btn, L"Подождите…");
        CreateThread(NULL, 0, worker, NULL, 0, NULL);
        return 0;
      }
      return 0;

    case WM_ST_TEXT:
      SetWindowTextW(g_status, (LPCWSTR)lp);
      return 0;
    case WM_ST_PROG:
      SendMessageW(g_bar, PBM_SETPOS, (WPARAM)lp, 0);
      return 0;
    case WM_ST_DONE:
      g_busy = false;
      EnableWindow(g_btn, TRUE);
      if (wp == 0) {
        g_ready = true;
        SetWindowTextW(g_btn, L"Запустить");
        SetWindowTextW(g_status, L"Установка завершена — можно запускать.");
      } else {
        SetWindowTextW(g_btn, L"Повторить");
        SetWindowTextW(g_status, g_err.empty() ? L"Ошибка установки." : g_err.c_str());
        SendMessageW(g_bar, PBM_SETPOS, 0, 0);
      }
      return 0;

    case WM_CLOSE:
      DestroyWindow(wnd);
      return 0;
    case WM_DESTROY:
      PostQuitMessage(0);
      return 0;
  }
  return DefWindowProcW(wnd, msg, wp, lp);
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR, int nCmdShow) {
  INITCOMMONCONTROLSEX icc;
  icc.dwSize = sizeof(icc);
  icc.dwICC = ICC_PROGRESS_CLASS | ICC_WIN95_CLASSES;
  InitCommonControlsEx(&icc);

  g_dir = envOr(L"ATK_SETUP_DIR",
                []() {
                  wchar_t b[MAX_PATH];
                  if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA, NULL,
                                                 SHGFP_TYPE_CURRENT, b)))
                    return std::wstring(b) + L"\\AttackLauncher";
                  return std::wstring(L"C:\\AttackLauncher");
                }());

  /* режим для проверки/автоустановки: AttackLauncherSetup.exe --silent */
  if (hasSwitch(L"--silent")) {
    std::wstring serr;
    bool ok = runInstall(serr);
    std::wstring log = envOr(L"TEMP", L"C:\\Windows\\Temp") + L"\\atk-setup-result.txt";
    std::wstring text = std::wstring(L"ok=") + (ok ? L"1" : L"0") +
                        L"\r\nerr=" + (ok ? L"-" : serr) +
                        L"\r\nurl=" + envOr(L"ATK_SETUP_URL", kZipUrl) +
                        L"\r\ndir=" + g_dir +
                        L"\r\nzip=" + g_zip + L"\r\n";
    HANDLE f = CreateFileW(log.c_str(), GENERIC_WRITE, 0, NULL,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f != INVALID_HANDLE_VALUE) {
      DWORD wr = 0;
      WriteFile(f, text.c_str(), (DWORD)(text.size() * sizeof(wchar_t)), &wr, NULL);
      CloseHandle(f);
    }
    return ok ? 0 : 1;
  }

  WNDCLASSEXW wc;
  ZeroMemory(&wc, sizeof(wc));
  wc.cbSize = sizeof(wc);
  wc.style = CS_HREDRAW | CS_VREDRAW;
  wc.lpfnWndProc = wndProc;
  wc.hInstance = hInst;
  wc.hCursor = LoadCursorW(NULL, IDC_ARROW);
  wc.hIcon = LoadIconW(NULL, IDI_APPLICATION);
  wc.lpszClassName = L"AttackDLCSetupWnd";
  if (!RegisterClassExW(&wc)) return 1;

  int w = 520, h = 268;
  HWND wnd = CreateWindowExW(0, wc.lpszClassName, L"AttackDLC — установка",
                             WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_VISIBLE,
                             CW_USEDEFAULT, CW_USEDEFAULT, w, h,
                             NULL, NULL, hInst, NULL);
  if (!wnd) return 1;
  centerWindow(wnd, w, h);
  ShowWindow(wnd, nCmdShow);
  UpdateWindow(wnd);

  MSG msg;
  while (GetMessageW(&msg, NULL, 0, 0) > 0) {
    TranslateMessage(&msg);
    DispatchMessageW(&msg);
  }
  return (int)msg.wParam;
}

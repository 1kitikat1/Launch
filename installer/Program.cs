using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace NemesisSetup
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
        }
    }

    /** Данные последнего релиза, взятые из GitHub API. */
    public sealed class ReleaseInfo
    {
        public string Version;   // tag, например v1.4.1
        public string Notes;     // body релиза
        public string ZipUrl;    // browser_download_url ассета NemesisLauncher-*.zip
        public long Size;        // размер ассета в байтах
    }

    public sealed class SetupForm : Form
    {
        // фирменные цвета Nemesis
        private static readonly Color BgTop = ColorTranslator.FromHtml("#0A0F1A");
        private static readonly Color BgBottom = ColorTranslator.FromHtml("#0E1420");
        private static readonly Color Border = ColorTranslator.FromHtml("#202B3D");
        private static readonly Color Accent = ColorTranslator.FromHtml("#4C8DFF");
        private static readonly Color TextMain = ColorTranslator.FromHtml("#F5F8FF");
        private static readonly Color TextDim = ColorTranslator.FromHtml("#8E9BB2");
        private static readonly Color Danger = ColorTranslator.FromHtml("#FF5F57");

        private const string InstallDir = @"C:\NemesisLauncher";
        private const string ApiUrl =
            "https://api.github.com/repos/1kitikat1/Launch/releases/latest";

        private enum St { Loading, Ready, Working, Done, Error }

        private St _state = St.Loading;
        private ReleaseInfo _release;
        private bool _cleanupOnClose = true;
        private DateTime _lastProgressPaint = DateTime.MinValue;

        // управление — всё видно сразу, без каскадов
        private readonly RoundedButton _button;
        private readonly ProgressControl _progress;
        private readonly Label _statusLabel;
        private readonly Icon _icon;
        private readonly Bitmap _iconBitmap;

        // плавное появление окна
        private float _fade = 0f;
        private readonly System.Windows.Forms.Timer _fadeTimer;

        public SetupForm()
        {
            Text = "Установка Nemesis Launcher";
            ClientSize = new Size(620, 372);
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            DoubleBuffered = true;
            BackColor = BgTop;
            KeyPreview = true;
            Opacity = 0f;
            Font = new Font("Segoe UI", 9.5f);

            _icon = LoadIcon();
            _iconBitmap = _icon != null ? _icon.ToBitmap() : null;

            // кнопки окна справа вверху — как в лаунчере
            var winMin = new WindowControl("min", "Свернуть")
            {
                Location = new Point(ClientSize.Width - 100, 14)
            };
            var winMax = new WindowControl("max", "Развернуть")
            {
                Location = new Point(ClientSize.Width - 72, 14)
            };
            var winClose = new WindowControl("close", "Закрыть")
            {
                Location = new Point(ClientSize.Width - 44, 14)
            };
            winMin.Click += delegate { WindowState = FormWindowState.Minimized; };
            winMax.Click += delegate
            {
                WindowState = WindowState == FormWindowState.Maximized
                    ? FormWindowState.Normal
                    : FormWindowState.Maximized;
            };
            winClose.Click += delegate { Close(); };
            Controls.Add(winMin);
            Controls.Add(winMax);
            Controls.Add(winClose);

            // кнопка установки (единая сетка: x=40, ширина=540)
            _button = new RoundedButton
            {
                Location = new Point(40, 170),
                Size = new Size(540, 56),
                Text = "ЗАГРУЗКА…",
                Enabled = false
            };
            _button.Click += OnButtonClick;
            Controls.Add(_button);

            // прогресс
            _progress = new ProgressControl
            {
                Location = new Point(40, 250),
                Size = new Size(540, 14)
            };
            Controls.Add(_progress);

            // статус — виден с самого начала, по центру
            _statusLabel = new Label
            {
                Location = new Point(40, 278),
                Size = new Size(540, 34),
                Font = new Font("Segoe UI", 9.5f),
                ForeColor = TextDim,
                BackColor = Color.Transparent,
                TextAlign = ContentAlignment.MiddleCenter,
                Text = "Проверяем доступную версию…"
            };
            Controls.Add(_statusLabel);

            // плавное появление окна (полупрозрачность -> 1)
            _fadeTimer = new System.Windows.Forms.Timer { Interval = 16 };
            _fadeTimer.Tick += delegate
            {
                _fade += 0.06f;
                if (_fade >= 1f)
                {
                    _fade = 1f;
                    _fadeTimer.Stop();
                }
                Opacity = _fade;
            };

            Load += async delegate
            {
                _fadeTimer.Start();
                await LoadReleaseAsync();
            };
        }

        /* =========================================================
         * ЛОГИКА
         * ========================================================= */

        private async Task LoadReleaseAsync()
        {
            SetState(St.Loading, "Проверяем доступную версию…", null);
            try
            {
                using (var client = CreateClient())
                {
                    _release = await FetchLatestAsync(client);
                }

                string size = _release.Size > 0
                    ? " · " + Mb(_release.Size)
                    : "";
                SetState(St.Ready,
                    "Готово к установке" + size + " · папка " + InstallDir,
                    "УСТАНОВИТЬ");
            }
            catch (Exception ex)
            {
                SetState(St.Error, "Ошибка: " + Friendly(ex), "ПОВТОРИТЬ");
            }
        }

        private async void OnButtonClick(object sender, EventArgs e)
        {
            if (_state == St.Done)
            {
                try
                {
                    var psi = new ProcessStartInfo(Path.Combine(InstallDir, "NemesisLauncher.exe"))
                    {
                        WorkingDirectory = InstallDir
                    };
                    Process.Start(psi);
                    _cleanupOnClose = false;
                }
                catch (Exception ex)
                {
                    SetState(St.Error, "Не удалось запустить: " + Friendly(ex), "ПОВТОРИТЬ");
                    return;
                }
                Close();
                return;
            }

            if (_state == St.Error)
            {
                if (_release == null)
                {
                    await LoadReleaseAsync();
                }
                else
                {
                    await InstallAsync();
                }
                return;
            }

            if (_state == St.Ready)
            {
                await InstallAsync();
            }
        }

        private async Task InstallAsync()
        {
            string work = Path.Combine(Path.GetTempPath(), "nemesis-setup");
            string zip = Path.Combine(work, "release.zip");
            string staging = Path.Combine(work, "stg");

            SetState(St.Working, "Подготовка…", "УСТАНОВКА…");
            _progress.Value = 0;

            try
            {
                if (Process.GetProcessesByName("NemesisLauncher").Length > 0 ||
                    GameUsingLauncherRuntime())
                {
                    throw new InvalidOperationException(
                        "Закройте Nemesis Launcher и запущенную из него игру, " +
                        "затем нажмите «Повторить».");
                }

                if (Directory.Exists(work)) Directory.Delete(work, true);
                Directory.CreateDirectory(work);

                // ---------- скачивание ----------
                using (var client = CreateClient())
                {
                    client.Timeout = TimeSpan.FromMinutes(15);
                    Report(0.0, "Скачивание…");

                    using (var resp = await client.GetAsync(
                        _release.ZipUrl, HttpCompletionOption.ResponseHeadersRead))
                    {
                        resp.EnsureSuccessStatusCode();
                        long total = resp.Content.Headers.ContentLength ??
                                     (_release.Size > 0 ? _release.Size : -1);

                        using (var input = await resp.Content.ReadAsStreamAsync())
                        using (var output = File.Create(zip))
                        {
                            var buffer = new byte[1024 * 128];
                            long done = 0;
                            int read;
                            while ((read = await input.ReadAsync(buffer, 0, buffer.Length)) > 0)
                            {
                                output.Write(buffer, 0, read);
                                done += read;
                                if (total > 0)
                                {
                                    Report(0.45 * done / total,
                                        "Скачивание: " + Mb(done) + " из " + Mb(total));
                                }
                            }
                        }
                    }
                }

                // ---------- распаковка ----------
                Report(0.45, "Распаковка архива…");
                if (Directory.Exists(staging)) Directory.Delete(staging, true);
                Directory.CreateDirectory(staging);

                int entriesCount;
                var entries = new List<ZipArchiveEntry>();
                using (var archive = ZipFile.OpenRead(zip))
                {
                    entries.AddRange(archive.Entries);
                    entriesCount = entries.Count;
                    if (entriesCount == 0) throw new InvalidDataException("Архив пуст.");

                    int i = 0;
                    foreach (var entry in entries)
                    {
                        i++;
                        string dest = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                        if (!dest.StartsWith(Path.GetFullPath(staging), StringComparison.OrdinalIgnoreCase))
                        {
                            throw new InvalidDataException("Небезопасный путь в архиве.");
                        }

                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(dest);
                        }
                        else
                        {
                            Directory.CreateDirectory(Path.GetDirectoryName(dest));
                            entry.ExtractToFile(dest, true);
                        }

                        if (i % 40 == 0 || i == entriesCount)
                        {
                            Report(0.45 + 0.35 * i / entriesCount,
                                "Распаковка: " + i + " из " + entriesCount);
                        }
                    }
                }

                // ---------- перенос в C:\NemesisLauncher ----------
                Report(0.82, "Установка в " + InstallDir + "…");

                string top = Directory.GetDirectories(staging).FirstOrDefault();
                if (top == null)
                {
                    // архив без верхней папки — распакованное содержимое и есть установка
                    top = staging;
                }

                if (Process.GetProcessesByName("NemesisLauncher").Length > 0 ||
                    GameUsingLauncherRuntime())
                {
                    throw new InvalidOperationException(
                        "Закройте Nemesis Launcher и запущенную из него игру, " +
                        "затем нажмите «Повторить».");
                }

                if (Directory.Exists(InstallDir))
                {
                    foreach (string f in Directory.GetFiles(InstallDir, "*", SearchOption.AllDirectories))
                    {
                        try { File.SetAttributes(f, FileAttributes.Normal); } catch { }
                    }
                    Directory.Delete(InstallDir, true);
                }

                if (string.Equals(Path.GetFullPath(top), Path.GetFullPath(staging),
                        StringComparison.OrdinalIgnoreCase))
                {
                    Directory.Move(staging, InstallDir);
                }
                else
                {
                    Directory.Move(top, InstallDir);
                }

                Report(0.92, "Создаём ярлыки…");
                CreateShortcuts(Path.Combine(InstallDir, "NemesisLauncher.exe"));

                // ---------- готово ----------
                Report(1.0, "Готово. Файлы установлены в " + InstallDir);
                SetState(St.Done, "Готово! Можно запускать.", "ЗАПУСТИТЬ");
            }
            catch (Exception ex)
            {
                SetState(St.Error, Friendly(ex), "ПОВТОРИТЬ");
            }
            finally
            {
                try { if (Directory.Exists(work)) Directory.Delete(work, true); } catch { }
            }
        }

        private void CreateShortcuts(string exe)
        {
            dynamic shell = Activator.CreateInstance(
                Type.GetTypeFromProgID("WScript.Shell"));

            string desktop = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
                "Nemesis Launcher.lnk");
            dynamic d = shell.CreateShortcut(desktop);
            d.TargetPath = exe;
            d.WorkingDirectory = InstallDir;
            d.IconLocation = exe + ",0";
            d.Description = "Nemesis Launcher";
            d.Save();

            string menu = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
                "Programs", "Nemesis Launcher.lnk");
            dynamic m = shell.CreateShortcut(menu);
            m.TargetPath = exe;
            m.WorkingDirectory = InstallDir;
            m.IconLocation = exe + ",0";
            m.Description = "Nemesis Launcher";
            m.Save();
        }

        /* =========================================================
         * GitHub API
         * ========================================================= */

        private static HttpClient CreateClient()
        {
            var client = new HttpClient();
            client.DefaultRequestHeaders.UserAgent.ParseAdd("NemesisSetup");
            client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
            client.Timeout = TimeSpan.FromSeconds(30);
            return client;
        }

        private static async Task<ReleaseInfo> FetchLatestAsync(HttpClient client)
        {
            string json = await client.GetStringAsync(ApiUrl);
            var serializer = new JavaScriptSerializer();
            var map = (Dictionary<string, object>)serializer.DeserializeObject(json);

            string tag = map.ContainsKey("tag_name") ? Convert.ToString(map["tag_name"]) : "";
            string notes = map.ContainsKey("body") && map["body"] != null
                ? Convert.ToString(map["body"])
                : "";

            string url = null;
            long size = 0;
            string fallbackUrl = null;
            long fallbackSize = 0;

            if (map.ContainsKey("assets"))
            {
                object assetsObj = map["assets"];
                var enumerable = assetsObj as IEnumerable;
                if (enumerable != null && !(assetsObj is string))
                {
                    foreach (object item in enumerable)
                    {
                        var a = item as Dictionary<string, object>;
                        if (a == null || !a.ContainsKey("name") ||
                            !a.ContainsKey("browser_download_url"))
                        {
                            continue;
                        }

                        string name = Convert.ToString(a["name"]);
                        string assetUrl = Convert.ToString(a["browser_download_url"]);
                        long assetSize = a.ContainsKey("size")
                            ? Convert.ToInt64(a["size"])
                            : 0;

                        if (name.StartsWith("NemesisLauncher-",
                                StringComparison.OrdinalIgnoreCase) &&
                            name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
                        {
                            url = assetUrl;
                            size = assetSize;
                            break;
                        }

                        // запасной вариант: любой zip из релиза
                        if (fallbackUrl == null &&
                            name.EndsWith(".zip", StringComparison.OrdinalIgnoreCase))
                        {
                            fallbackUrl = assetUrl;
                            fallbackSize = assetSize;
                        }
                    }
                }
            }

            if (url == null && fallbackUrl != null)
            {
                url = fallbackUrl;
                size = fallbackSize;
            }

            if (string.IsNullOrEmpty(url))
            {
                throw new InvalidOperationException("Не найден архив для установки.");
            }
            if (string.IsNullOrEmpty(tag))
            {
                throw new InvalidOperationException("Не удалось определить версию.");
            }

            return new ReleaseInfo
            {
                Version = tag,
                Notes = notes,
                ZipUrl = url,
                Size = size
            };
        }

        /* =========================================================
         * UI
         * ========================================================= */

        private void SetState(St state, string status, string buttonText)
        {
            if (IsDisposed) return;

            _state = state;
            _statusLabel.ForeColor = state == St.Error ? Danger : TextDim;
            _statusLabel.Text = status;

            if (buttonText == null || state == St.Working)
            {
                _button.Enabled = false;
                _button.Text = state == St.Working ? "УСТАНОВКА…" : "ЗАГРУЗКА…";
            }
            else
            {
                _button.Text = buttonText;
                _button.Enabled = true;
            }
            _button.Invalidate();
            _statusLabel.Invalidate();
        }

        private void Report(double fraction, string text)
        {
            if (IsDisposed || !IsHandleCreated) return;
            var now = DateTime.UtcNow;
            if ((now - _lastProgressPaint).TotalMilliseconds < 70 &&
                fraction < 1.0 && fraction > 0)
            {
                return;
            }
            _lastProgressPaint = now;

            _progress.Value = Math.Max(0, Math.Min(1, fraction));
            _statusLabel.Text = text;
            _progress.Invalidate();
            _statusLabel.Invalidate();
            Application.DoEvents();
        }

        private static string Mb(long bytes)
        {
            return (bytes / 1048576.0).ToString("0.0",
                       CultureInfo.GetCultureInfo("ru-RU")) + " МБ";
        }

        private static string Friendly(Exception ex)
        {
            if (ex is HttpRequestException || ex is TaskCanceledException)
                return "Нет связи с интернетом. Проверьте подключение и нажмите «Повторить».";
            if (ex is UnauthorizedAccessException || ex is IOException)
            {
                var io = ex as IOException;
                if (io != null && io.Message.Contains("библиотек"))
                    return "Файлы заняты: закройте лаунчер и нажмите «Повторить».";
            }
            return ex.Message;
        }

        private static bool GameUsingLauncherRuntime()
        {
            foreach (string name in new[] { "java", "javaw" })
            {
                foreach (Process p in Process.GetProcessesByName(name))
                {
                    try
                    {
                        string exe = p.MainModule != null ? p.MainModule.FileName : null;
                        if (exe != null &&
                            exe.StartsWith(InstallDir, StringComparison.OrdinalIgnoreCase))
                            return true;
                    }
                    catch
                    {
                        // чужой/защищённый процесс — пропускаем
                    }
                }
            }
            return false;
        }

        private static Icon LoadIcon()
        {
            try
            {
                Stream s = Assembly.GetExecutingAssembly()
                    .GetManifestResourceStream("icon.ico");
                if (s != null)
                {
                    // берём крупный кадр (256px) — иначе масштабирование даёт мыло
                    try { return new Icon(s, new Size(256, 256)); }
                    catch { return new Icon(s); }
                }
            }
            catch { }
            return SystemIcons.Application;
        }

        protected override void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            ApplyRegion();
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            ApplyRegion();
        }

        [DllImport("gdi32.dll")]
        private static extern IntPtr CreateRoundRectRgn(
            int nLeftRect, int nTopRect, int nRightRect, int nBottomRect,
            int nWidthEllipse, int nHeightEllipse);

        [DllImport("gdi32.dll")]
        private static extern bool DeleteObject(IntPtr hObject);

        private void ApplyRegion()
        {
            IntPtr hrgn = CreateRoundRectRgn(0, 0, Width + 1, Height + 1, 22, 22);
            if (hrgn == IntPtr.Zero) return;
            Region old = Region;
            try
            {
                Region = Region.FromHrgn(hrgn);
            }
            finally
            {
                DeleteObject(hrgn);
                if (old != null) old.Dispose();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            var rect = new Rectangle(0, 0, ClientSize.Width, ClientSize.Height);
            using (var grad = new LinearGradientBrush(rect, BgTop, BgBottom, 90f))
            {
                g.FillRectangle(grad, rect);
            }

            // рамка окна
            using (var frame = new Pen(Border, 1))
            {
                g.DrawRectangle(frame, 0.5f, 0.5f, ClientSize.Width - 2f, ClientSize.Height - 2f);
            }

            // мягкое свечение за иконкой (радиальное)
            var iconRect = new Rectangle(40, 56, 64, 64);
            var glowRect = new Rectangle(22, 38, 100, 100);
            var glowPath = new GraphicsPath();
            glowPath.AddEllipse(glowRect);
            using (var glow = new PathGradientBrush(glowPath))
            {
                glow.CenterColor = Color.FromArgb(46, 76, 141, 255);
                glow.SurroundColors = new[] { Color.FromArgb(0, 76, 141, 255) };
                g.FillPath(glow, glowPath);
            }
            glowPath.Dispose();

            // иконка (по центру свечения)
            if (_iconBitmap != null)
            {
                g.DrawImage(_iconBitmap, iconRect);
            }

            // заголовок и подзаголовок — единый блок, отцентрован по иконке
            using (var title = new Font("Segoe UI Semibold", 17f, FontStyle.Bold))
            using (var sub = new Font("Segoe UI", 9.5f))
            using (var titleBrush = new SolidBrush(TextMain))
            using (var subBrush = new SolidBrush(TextDim))
            {
                var titleSz = g.MeasureString("NEMESIS LAUNCHER", title);
                var subSz = g.MeasureString("Установка в один клик", sub);
                float blockH = titleSz.Height + 4 + subSz.Height;
                float top = iconRect.Y + (iconRect.Height - blockH) / 2f;
                float x = iconRect.Right + 24;

                g.DrawString("NEMESIS LAUNCHER", title, titleBrush, x, top);
                g.DrawString("Установка в один клик", sub, subBrush, x + 2, top + titleSz.Height + 4);
            }

            // акцентная линия под шапкой — с плавным затуханием вправо
            var lineRect = new Rectangle(40, 146, 540, 2);
            using (var lineBrush = new LinearGradientBrush(lineRect,
                Accent, Color.FromArgb(0, 76, 141, 255), 0f))
            {
                g.FillRectangle(lineBrush, lineRect);
            }

            // подвал — по центру
            using (var foot = new Font("Segoe UI", 8.75f))
            using (var brush = new SolidBrush(Color.FromArgb(107, 116, 168)))
            {
                string footText =
                    "Установка: " + InstallDir + "   ·   не требует прав администратора";
                var sz = g.MeasureString(footText, foot);
                g.DrawString(footText, foot, brush,
                    (ClientSize.Width - sz.Width) / 2f, 340);
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                if (_cleanupOnClose)
                {
                    try
                    {
                        string work = Path.Combine(Path.GetTempPath(), "nemesis-setup");
                        if (Directory.Exists(work)) Directory.Delete(work, true);
                    }
                    catch { }
                }
                _fadeTimer?.Stop();
                _fadeTimer?.Dispose();
                _iconBitmap?.Dispose();
                _icon?.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    /* =========================================================
     * ЭЛЕМЕНТЫ УПРАВЛЕНИЯ
     * ========================================================= */

    public static class Ui
    {
        public static GraphicsPath RoundedRect(Rectangle r, int radius)
        {
            int d = radius * 2;
            var path = new GraphicsPath();
            if (d <= 0)
            {
                path.AddRectangle(r);
                return path;
            }
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }
    }

    /** Кнопка окна с векторным глифом — как кнопки окна в лаунчере. */
    public sealed class WindowControl : Control
    {
        private bool _hover;
        private readonly string _kind;

        public WindowControl(string kind, string tooltip)
        {
            _kind = kind;
            Size = new Size(28, 28);
            Cursor = Cursors.Hand;
            SetStyle(ControlStyles.AllPaintingInWmPaint |
                     ControlStyles.OptimizedDoubleBuffer |
                     ControlStyles.UserPaint |
                     ControlStyles.SupportsTransparentBackColor |
                     ControlStyles.ResizeRedraw, true);
            BackColor = Color.Transparent;

            var tip = new ToolTip();
            tip.SetToolTip(this, tooltip);

            MouseEnter += (s, e) => { _hover = true; Invalidate(); };
            MouseLeave += (s, e) => { _hover = false; Invalidate(); };
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            var isClose = _kind == "close";
            Color glyph = isClose && _hover
                ? Color.White
                : ColorTranslator.FromHtml("#8E9BB2");

            if (_hover)
            {
                using (var bg = new SolidBrush(isClose
                    ? ColorTranslator.FromHtml("#FF5F57")
                    : ColorTranslator.FromHtml("#1A2333")))
                {
                    g.FillRectangle(bg, 0, 0, Width - 1, Height - 1);
                }
            }

            using (var pen = new Pen(glyph, 1.2f))
            {
                pen.LineJoin = LineJoin.Round;
                pen.StartCap = LineCap.Round;
                pen.EndCap = LineCap.Round;

                int cx = Width / 2;
                int cy = Height / 2;
                int s = 4;

                if (_kind == "close")
                {
                    g.DrawLine(pen, cx - s, cy - s, cx + s, cy + s);
                    g.DrawLine(pen, cx + s, cy - s, cx - s, cy + s);
                }
                else if (_kind == "max")
                {
                    g.DrawRectangle(pen, cx - s, cy - s, s * 2, s * 2);
                }
                else
                {
                    g.DrawLine(pen, cx - s, cy, cx + s, cy);
                }
            }
        }
    }

    /** Кнопка со скруглением и градиентом — как primary-button в лаунчере. */
    public sealed class RoundedButton : Control
    {
        private bool _hover;
        private bool _pressed;

        public RoundedButton()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint |
                     ControlStyles.OptimizedDoubleBuffer |
                     ControlStyles.UserPaint |
                     ControlStyles.SupportsTransparentBackColor |
                     ControlStyles.ResizeRedraw, true);
            Cursor = Cursors.Hand;
            BackColor = Color.Transparent;
        }

        protected override void OnMouseEnter(EventArgs e)
        {
            _hover = true;
            Invalidate();
            base.OnMouseEnter(e);
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            _hover = false;
            _pressed = false;
            Invalidate();
            base.OnMouseLeave(e);
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            if (e.Button == MouseButtons.Left && Enabled) _pressed = true;
            Invalidate();
            base.OnMouseDown(e);
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            _pressed = false;
            Invalidate();
            base.OnMouseUp(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            var rect = new Rectangle(0, 0, Width - 1, Height - 1);

            if (!Enabled)
            {
                using (var path = Ui.RoundedRect(rect, 14))
                using (var bg = new SolidBrush(ColorTranslator.FromHtml("#141B29")))
                using (var pen = new Pen(ColorTranslator.FromHtml("#243249")))
                {
                    g.FillPath(bg, path);
                    g.DrawPath(pen, path);
                }
            }
            else
            {
                Color from = _pressed
                    ? ColorTranslator.FromHtml("#3E78E0")
                    : _hover
                        ? ColorTranslator.FromHtml("#66A0FF")
                        : ColorTranslator.FromHtml("#4C8DFF");
                Color to = _pressed
                    ? ColorTranslator.FromHtml("#4C8DFF")
                    : _hover
                        ? ColorTranslator.FromHtml("#7FA8FF")
                        : ColorTranslator.FromHtml("#3E78E0");

                using (var path = Ui.RoundedRect(rect, 14))
                using (var brush = new LinearGradientBrush(rect, from, to, 0f))
                {
                    g.FillPath(brush, path);
                    using (var pen = new Pen(ColorTranslator.FromHtml("#8CAEFF"), 1))
                    {
                        g.DrawPath(pen, path);
                    }
                }
            }

            using (var font = new Font("Segoe UI Semibold", 11.5f, FontStyle.Bold))
            {
                var sz = g.MeasureString(Text, font);
                var pt = new PointF((Width - sz.Width) / 2f, (Height - sz.Height) / 2f);
                g.DrawString(Text, font,
                    Enabled
                        ? Brushes.White
                        : new SolidBrush(ColorTranslator.FromHtml("#6E7B90")),
                    pt);
            }
        }
    }

    /** Тонкий прогресс со скруглением — акцентный. */
    public sealed class ProgressControl : Control
    {
        private double _value;

        public double Value
        {
            get { return _value; }
            set { _value = value; Invalidate(); }
        }

        public ProgressControl()
        {
            SetStyle(ControlStyles.AllPaintingInWmPaint |
                     ControlStyles.OptimizedDoubleBuffer |
                     ControlStyles.UserPaint |
                     ControlStyles.SupportsTransparentBackColor |
                     ControlStyles.ResizeRedraw, true);
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            var track = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = Ui.RoundedRect(track, Height / 2))
            using (var bg = new SolidBrush(ColorTranslator.FromHtml("#171F30")))
            {
                g.FillPath(bg, path);
            }

            int w = (int)(Width * Math.Max(0, Math.Min(1, _value)));
            if (w > Height)
            {
                var fill = new Rectangle(0, 0, w - 1, Height - 1);
                using (var path = Ui.RoundedRect(fill, Height / 2))
                using (var brush = new LinearGradientBrush(fill,
                    ColorTranslator.FromHtml("#4C8DFF"), ColorTranslator.FromHtml("#7FA8FF"), 0f))
                {
                    g.FillPath(brush, path);
                }
            }

            if (_value > 0.02)
            {
                string text = ((int)Math.Round(_value * 100)) + "%";
                using (var font = new Font("Segoe UI", 8f, FontStyle.Bold))
                {
                    var sz = g.MeasureString(text, font);
                    g.DrawString(text, font, Brushes.White,
                        new PointF(w - sz.Width - 8, (Height - sz.Height) / 2f));
                }
            }
        }
    }
}

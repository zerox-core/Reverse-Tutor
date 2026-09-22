
# -*- coding: utf-8 -*-
import subprocess, sys, time, re
sys.stdout.reconfigure(encoding="utf-8")
ADB = "adb"
DEV = "emulator-5554"
MAIN = "com.reversetutor.preview.memtest"
LOG = open(r"F:\xw\reverse-tutor\aily_1b_e2e.log", "w", encoding="utf-8", errors="replace")

def log(*a):
    msg = " ".join(str(x) for x in a)
    print(msg)
    LOG.write(msg + "\n")
    LOG.flush()

def sh(args, timeout=60):
    r = subprocess.run([ADB, "-s", DEV] + args, capture_output=True, timeout=timeout)
    return (r.stdout or b"").decode("utf-8", "replace") + (r.stderr or b"").decode("utf-8", "replace")

def dump(name, tries=6, gap=2.0):
    xml = ""
    for i in range(tries):
        sh(["shell", "uiautomator", "dump", "/sdcard/" + name])
        xml = sh(["shell", "cat", "/sdcard/" + name], timeout=30)
        if len(xml) > 300:
            return xml
        time.sleep(gap)
    return xml

def tap_xy(x, y, label):
    log(f"tap {label} at {x},{y}")
    sh(["shell", "input", "tap", str(x), str(y)])

def tap_first(xml, patterns, label):
    for p in patterns:
        m = re.search(p, xml)
        if m:
            x = (int(m.group(1)) + int(m.group(3))) // 2
            y = (int(m.group(2)) + int(m.group(4))) // 2
            tap_xy(x, y, label)
            return True
    return False

def pt(key):
    e = re.escape(key)
    return [r'text="' + e + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="' + e + r'"']

def pd(key):
    e = re.escape(key)
    return [r'content-desc="' + e + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="' + e + r'"']

def show(xml, tag):
    texts = [t for t in re.findall(r'text="([^"]{1,40})"', xml) if t]
    descs = [t for t in re.findall(r'content-desc="([^"]{1,40})"', xml) if t]
    log(f"-- {tag} texts:", texts[-22:])
    log(f"-- {tag} descs:", descs[-12:])

# 0. 确保图片在位 + 重装最新 APK
log("== push image ==")
log(sh(["push", r"F:\xw\reverse-tutor\aily_e2e_img.png", "/sdcard/Pictures/aily_e2e_img.png"])[:120])
sh(["shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Pictures/aily_e2e_img.png"])
log("== install ==")
log(sh(["install", "-r", r"F:\xw\reverse-tutor\mobile-native\app\build\outputs\apk\debug\app-debug.apk"], timeout=300)[:200])

# 1. 启动进会话
sh(["shell", "am", "force-stop", MAIN])
time.sleep(1)
sh(["shell", "am", "start", "-n", MAIN + "/com.reversetutor.preview.MainActivity"])
time.sleep(9)
xml = dump("aily_e1.xml")
if not tap_first(xml, pt("小六子"), "session"):
    log("FATAL: session entry missing"); show(xml, "home"); sys.exit(1)
time.sleep(4)

# 2. 点附件入口
xml = dump("aily_e2.xml")
if not tap_first(xml, pd("添加图片或资料"), "attach"):
    log("FATAL: attach missing"); show(xml, "chat"); sys.exit(1)
time.sleep(3)

# 3. 处理权限框（可能已授权跳过）
xml = dump("aily_e3.xml")
show(xml, "after-attach")
if "Allow all" in xml:
    tap_first(xml, pt("Allow all"), "Allow all")
    time.sleep(3)
    xml = dump("aily_e4.xml")
    show(xml, "after-allow")
elif "Allow limited access" in xml:
    tap_first(xml, pt("Allow limited access"), "Allow limited")
    time.sleep(3)
    xml = dump("aily_e4.xml")
    show(xml, "after-allow-limited")
LOG.close()
print("PART1 DONE")

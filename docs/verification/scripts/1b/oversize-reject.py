
# -*- coding: utf-8 -*-
import subprocess, sys, os, zlib, struct, binascii, time, re
sys.stdout.reconfigure(encoding="utf-8")
ADB = "adb"
DEV = "emulator-5554"

def sh(args, timeout=120):
    r = subprocess.run([ADB, "-s", DEV] + args, capture_output=True, timeout=timeout)
    return (r.stdout or b"").decode("utf-8", "replace") + (r.stderr or b"").decode("utf-8", "replace")

# 1. 纯 stdlib 生成 ~21MB 随机噪声 PNG（zlib level 0 存储，保证体积超标）
W, H = 2700, 2600
path = r"F:\xw\reverse-tutor\aily_e2e_big.png"
def chunk(typ, data):
    c = struct.pack(">I", len(data)) + typ + data
    return c + struct.pack(">I", binascii.crc32(typ + data) & 0xffffffff)
ihdr = struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)
co = zlib.compressobj(0)
raw = b""
with open(path, "wb") as f:
    f.write(b"\x89PNG\r\n\x1a\n")
    f.write(chunk(b"IHDR", ihdr))
    idat_parts = []
    for y in range(H):
        row = b"\x00" + os.urandom(W * 3)
        idat_parts.append(co.compress(row))
    idat_parts.append(co.flush())
    f.write(chunk(b"IDAT", b"".join(idat_parts)))
    f.write(chunk(b"IEND", b""))
size = os.path.getsize(path)
print("big png bytes:", size, "over 20MB:", size > 20 * 1024 * 1024)

print(sh(["push", path, "/sdcard/Pictures/aily_e2e_big.png"], timeout=300)[:120])
sh(["shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file:///sdcard/Pictures/aily_e2e_big.png"])
time.sleep(2)

# 2. 走附件流程选大图
def dump(name, tries=6, gap=2.0):
    xml = ""
    for i in range(tries):
        sh(["shell", "uiautomator", "dump", "/sdcard/" + name])
        xml = sh(["shell", "cat", "/sdcard/" + name], timeout=30)
        if len(xml) > 300:
            return xml
        time.sleep(gap)
    return xml

def tap_first(xml, patterns, label):
    for p in patterns:
        m = re.search(p, xml)
        if m:
            x = (int(m.group(1)) + int(m.group(3))) // 2
            y = (int(m.group(2)) + int(m.group(4))) // 2
            print(f"tap {label} at {x},{y}")
            sh(["shell", "input", "tap", str(x), str(y)])
            return True
    return False

def pd_sub(key):
    e = re.escape(key)
    return [r'content-desc="[^"]*' + e + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"']

def pt(key):
    e = re.escape(key)
    return [r'text="' + e + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"']

def show(xml, tag):
    texts = [t for t in re.findall(r'text="([^"]{1,40})"', xml) if t]
    descs = [t for t in re.findall(r'content-desc="([^"]{1,40})"', xml) if t]
    print(f"-- {tag} texts:", texts[-20:])
    print(f"-- {tag} descs:", descs[-12:])

xml = dump("aily_h1.xml")
# 当前应在聊天页（上一轮结束态），若附件 chip 还在先移除
if "移除aily_e2e_img.png" in xml:
    tap_first(xml, pd_sub("移除aily_e2e_img.png"), "remove old chip")
    time.sleep(2)
    xml = dump("aily_h2.xml")

if not tap_first(xml, [r'content-desc="添加图片或资料"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'], "attach"):
    print("FATAL: attach missing"); show(xml, "chat"); sys.exit(1)
time.sleep(2.5)
xml = dump("aily_h3.xml")
if not tap_first(xml, pt("相册"), "相册"):
    print("FATAL: 相册 missing"); show(xml, "sheet"); sys.exit(1)
time.sleep(4)
xml = dump("aily_h4.xml")
if not tap_first(xml, pd_sub("aily_e2e_big.png"), "big image"):
    print("FATAL: big image not in picker"); show(xml, "picker"); sys.exit(1)
time.sleep(3)
xml = dump("aily_h5.xml")
show(xml, "after-big-select")
print("contains 20 MB notice:", "20 MB" in xml or "20MB" in xml)
print("contains chip 已准备:", "已准备" in xml)
print("BIG-TEST DONE")

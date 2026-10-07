import subprocess
subprocess.Popen([r"E:\Android\Sdk\emulator\emulator.exe", "-avd", "rt_test", "-no-snapshot-save"], creationflags=subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, close_fds=True)
print("launched")

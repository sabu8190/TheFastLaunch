import os
import sys
import shutil
import subprocess

sys.stdout.reconfigure(encoding='utf-8')

ROOT = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.join(os.path.dirname(ROOT), "dist")

PROJECTS = [
    {
        "name": "Forge 1.20.1",
        "dir": ROOT,
        "jar_path": os.path.join(ROOT, "build", "libs", "TheFastLaunch-b1.9.1-forge-1.20.1.jar"),
        "dist_name": "TheFastLaunch-b1.9.1-forge-1.20.1.jar",
        "instance_mods": r"D:\Minecraft\GDLauncher\instances\The Eternal World of Fantasy-Tale of the End--0.b5.2.zip\instance\mods"
    },
    {
        "name": "Fabric 1.20.1",
        "dir": os.path.join(ROOT, "fabric"),
        "jar_path": os.path.join(ROOT, "fabric", "build", "libs", "TheFastLaunch-b1.9.1-fabric-1.20.1.jar"),
        "dist_name": "TheFastLaunch-b1.9.1-fabric-1.20.1.jar",
        "instance_mods": r"D:\Minecraft\GDLauncher\instances\DarkRPG Fabric-9.0.7.zip\instance\mods"
    },
    {
        "name": "Fabric 1.21.1",
        "dir": os.path.join(ROOT, "fabric-1.21.1"),
        "jar_path": os.path.join(ROOT, "fabric-1.21.1", "build", "libs", "TheFastLaunch-b1.9.1-fabric-1.21.1.jar"),
        "dist_name": "TheFastLaunch-b1.9.1-fabric-1.21.1.jar",
        "instance_mods": r"D:\Minecraft\GDLauncher\instances\Guild Adventures v2.7.2.zip\instance\mods"
    },
    {
        "name": "NeoForge 1.21.1",
        "dir": os.path.join(ROOT, "neoforge-1.21.1"),
        "jar_path": os.path.join(ROOT, "neoforge-1.21.1", "build", "libs", "TheFastLaunch-b1.9.1-neoforge-1.21.1.jar"),
        "dist_name": "TheFastLaunch-b1.9.1-neoforge-1.21.1.jar",
        "instance_mods": r"D:\Minecraft\GDLauncher\instances\Vagrant Saga-1.2.0.zip\instance\mods"
    }
]

print("==================================================")
print("🚀 TheFastLaunch Multi-Loader Master Deployer")
print("==================================================")

os.makedirs(DIST, exist_ok=True)

for p in PROJECTS:
    name = p["name"]
    p_dir = p["dir"]
    jar_path = p["jar_path"]
    dist_name = p["dist_name"]
    dest_dir = p["instance_mods"]

    print(f"\n[Target: {name}]")
    if not os.path.exists(jar_path):
        print(f"  Building {name}...")
        gradlew = os.path.join(p_dir, "gradlew.bat")
        res = subprocess.run([gradlew, "build"], cwd=p_dir, capture_output=True, text=True)
        if res.returncode != 0:
            print(f"  [ERROR] Build failed: {res.stderr}")
            continue

    if os.path.exists(jar_path):
        # 1. Copy to dist/
        dist_path = os.path.join(DIST, dist_name)
        shutil.copy2(jar_path, dist_path)
        print(f"  -> dist: {dist_name} ({os.path.getsize(dist_path)} bytes)")

        # 2. Copy to instance/mods
        if dest_dir and os.path.exists(dest_dir):
            for old_f in os.listdir(dest_dir):
                if "fastlaunch" in old_f.lower() and old_f.endswith(".jar"):
                    os.remove(os.path.join(dest_dir, old_f))
                    print(f"  -> Cleaned old instance jar: {old_f}")
            inst_target = os.path.join(dest_dir, dist_name)
            shutil.copy2(jar_path, inst_target)
            print(f"  -> instance mods: {inst_target}")
        elif dest_dir:
            print(f"  [WARN] Instance dir not found: {dest_dir}")

print("\n==================================================")
print("✅ All deployments completed successfully!")
print("==================================================")

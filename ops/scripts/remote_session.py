"""Ephemeral SSH session. Password is read once from stdin and never persisted."""
import base64
import hashlib
import getpass
import json
import sys
import time

import paramiko


class FirstContactPolicy(paramiko.MissingHostKeyPolicy):
    def missing_host_key(self, client, hostname, key):
        fingerprint = base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip("=")
        print(json.dumps({"host": hostname, "first_contact_key": "SHA256:" + fingerprint}), flush=True)


client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(FirstContactPolicy())
credential = getpass.getpass("SSH credential: ")
try:
    client.connect("47.109.86.76", username="root", password=credential, timeout=15,
                   auth_timeout=15, banner_timeout=15, look_for_keys=False, allow_agent=False)
finally:
    credential = None
print(json.dumps({"connected": True}), flush=True)
try:
    for line in sys.stdin:
        request = json.loads(line)
        if request.get("op") == "close":
            break
        if request.get("op") == "put":
            with client.open_sftp() as sftp:
                sftp.put(request["local"], request["remote"])
            print(json.dumps({"uploaded": request["remote"]}), flush=True)
            continue
        if request.get("op") == "get":
            with client.open_sftp() as sftp:
                sftp.get_channel().settimeout(60)
                sftp.get(request["remote"], request["local"], prefetch=False)
            print(json.dumps({"downloaded": request["local"]}), flush=True)
            continue
        _, stdout, stderr = client.exec_command(request["command"], timeout=request.get("timeout", 45))
        channel = stdout.channel
        chunks, errors = [], []
        deadline = time.monotonic() + request.get("timeout", 45)
        while not channel.exit_status_ready() or channel.recv_ready() or channel.recv_stderr_ready():
            if channel.recv_ready(): chunks.append(channel.recv(65536))
            if channel.recv_stderr_ready(): errors.append(channel.recv_stderr(65536))
            if time.monotonic() > deadline:
                channel.close()
                errors.append(b"Remote command timed out; check remote process before retrying.")
                break
            time.sleep(0.03)
        result = {"exit": channel.recv_exit_status(), "stdout": b"".join(chunks).decode("utf-8", "replace"),
                  "stderr": b"".join(errors).decode("utf-8", "replace")}
        print(json.dumps(result, ensure_ascii=False), flush=True)
finally:
    client.close()

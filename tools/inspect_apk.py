#!/usr/bin/env python
"""Inspects the built APK: dex method counts and presence of key classes."""
import os
import struct
import sys
import zipfile

KEY_CLASSES = [
    "Lcom/hierynomus/smbj/SMBClient;",
    "Lcom/hierynomus/smbj/share/DiskShare;",
    "Lcom/hierynomus/smbj/event/ConnectionClosed;",
    "Lcom/hierynomus/ntlm/NtlmConfig;",
    "Lorg/bouncycastle/crypto/digests/MD4Digest;",
    "Lorg/bouncycastle/crypto/engines/AESEngine;",
    "Lnet/engio/mbassy/bus/MBassador;",
    "Lcom/hierynomus/asn1/types/ASN1Object;",
    "Lorg/slf4j/LoggerFactory;",
    "Landroidx/security/crypto/EncryptedSharedPreferences;",
    "Lcom/google/crypto/tink/Aead;",
    "Lcom/nas2screen/screensaver/PhotoDreamService;",
    "Lcom/nas2screen/screensaver/ui/SettingsActivity;",
    "Lcom/nas2screen/screensaver/source/SmbPhotoSource;",
    "Lcom/nas2screen/screensaver/slideshow/SlideshowEngine;",
]


def dex_header(data):
    if data[:4] != b"dex\n":
        return None
    checksum, = struct.unpack_from("<I", data, 8)
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    method_ids_size, method_ids_off = struct.unpack_from("<II", data, 0x58)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)
    return {
        "file_size": len(data),
        "string_ids": string_ids_size,
        "method_ids": method_ids_size,
        "class_defs": class_defs_size,
    }


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "dist", "nas2screen-1.0.0-release.apk")
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        dexs = [n for n in names if n.endswith(".dex")]
        print("APK: %s (%d bytes, %d entries)" % (apk, os.path.getsize(apk), len(names)))
        print("dex files:", dexs)
        blobs = {n: z.read(n) for n in dexs}
        total_methods = 0
        for name, data in blobs.items():
            header = dex_header(data)
            if header:
                total_methods += header["method_ids"]
                print("  %-14s methods=%6d classes=%5d strings=%6d size=%7d"
                      % (name, header["method_ids"], header["class_defs"],
                         header["string_ids"], header["file_size"]))
        print("total methods: %d (multidex limit 65536 per dex)" % total_methods)
        haystack = b"\x00".join(blobs.values())
        print("key classes:")
        missing = []
        for cls in KEY_CLASSES:
            present = cls.encode() in haystack
            print("  %-70s %s" % (cls, "yes" if present else "NO"))
            if not present:
                missing.append(cls)
        if missing:
            print("MISSING:", ", ".join(missing))
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

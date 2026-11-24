"""Assemble l'appli Linko en un seul fichier : web/index.html
Usage :  python3 src/build.py
La page est construite avec src/app.src.html + src/icons.html + src/vendor/qrcode.js
et la config Supabase de src/config.json (adresse + cle publique "anon")."""
import json, os
here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(here)
rd = lambda *p: open(os.path.join(here, *p), encoding="utf-8").read()
s = rd("app.src.html")
lib = rd("vendor", "qrcode.js").replace("</script>", "<\\/script>")
cfg = json.load(open(os.path.join(here, "config.json"), encoding="utf-8"))
cfg = {k: v for k, v in cfg.items() if v}
s = s.replace("/*QRLIB*/", lib).replace("<!--ICONS-->", rd("icons.html"))
s = s.replace("/*LINKOCFG*/null", json.dumps(cfg) if cfg else "null")
out = os.path.join(root, "web", "index.html")
open(out, "w", encoding="utf-8").write(s)
print("OK", out, len(s), "octets")

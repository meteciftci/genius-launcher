package dev.evim.tv.data

/** Telefonda açılan kumanda sayfası. Dışarıdan hiçbir şey yüklemez; dil telefon tarayıcısının diline göre. */
internal object RemotePage {
    private val TR = mapOf(
        "lang" to "tr", "title" to "TV kumandası", "placeholder" to "TV'ye yazılacak metin", "send" to "Yaz",
        "error" to "TV'ye ulaşılamadı. TV'nin açık ve aynı Wi-Fi'de olduğundan emin olun.",
    )
    private val EN = mapOf(
        "lang" to "en", "title" to "TV remote", "placeholder" to "Text to type on the TV", "send" to "Type",
        "error" to "Couldn't reach the TV. Make sure it is on and on the same Wi-Fi.",
    )

    fun html(turkish: Boolean): String {
        val t = if (turkish) TR else EN
        val strings = t.entries.joinToString(",") { (k, v) -> "\"$k\":\"${v.replace("\"", "\\\"")}\"" }
        return """<!doctype html>
<html lang="${t["lang"]}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<title>${t["title"]}</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }
  body { margin: 0; padding: 18px 16px 28px; background: #0b0b0f; color: #fff; font: 16px/1.3 system-ui, -apple-system, sans-serif; }
  main { max-width: 360px; margin: 0 auto; }
  button { border: 0; color: #fff; background: rgba(255,255,255,.09); border-radius: 16px; font: inherit; font-weight: 600; height: 56px; touch-action: manipulation; }
  button:active { background: rgba(122,162,255,.35); }
  .pad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 6px 0 14px; }
  .pad button { height: 68px; font-size: 26px; }
  .ok { background: #7aa2ff; color: #0b0b0f; font-size: 17px !important; }
  .ok:active { background: #a6c0ff; }
  .row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
  .row.two { grid-template-columns: repeat(2, 1fr); }
  .text { display: flex; gap: 8px; margin-top: 18px; }
  input { flex: 1; min-width: 0; height: 56px; border-radius: 16px; border: 1px solid rgba(255,255,255,.18); background: rgba(255,255,255,.06); color: #fff; padding: 0 14px; font: inherit; user-select: text; }
  .text button { background: #7aa2ff; color: #0b0b0f; padding: 0 18px; }
  .note { color: #fbbf24; font-size: 14px; margin-top: 14px; display: none; text-align: center; }
</style>
</head>
<body>
<main>
  <div class="pad">
    <span></span><button data-k="up" data-hold>&#9650;</button><span></span>
    <button data-k="left" data-hold>&#9664;</button><button data-k="ok" class="ok">OK</button><button data-k="right" data-hold>&#9654;</button>
    <span></span><button data-k="down" data-hold>&#9660;</button><span></span>
  </div>
  <div class="row">
    <button data-k="back">&#8592;</button><button data-k="home">&#8962;</button><button data-k="play">&#9199;</button>
  </div>
  <div class="row">
    <button data-k="voldown" data-hold>&#128265; &minus;</button><button data-k="mute">&#128263;</button><button data-k="volup" data-hold>&#128266; +</button>
  </div>
  <form class="text" id="form">
    <input id="text" type="text" autocomplete="off" autocapitalize="off" placeholder="${t["placeholder"]}">
    <button type="submit">${t["send"]}</button>
  </form>
  <div class="row two" style="margin-top:8px">
    <button data-k="delete" data-hold>&#9003;</button><button data-k="enter">&#8629;</button>
  </div>
  <div class="note" id="note">${t["error"]}</div>
</main>
<script>
const T = {$strings};
const note = document.getElementById("note");
function post(path, body) {
  return fetch(path, { method: "POST", body }).then(r => { note.style.display = r.ok ? "none" : "block"; return r.ok; })
    .catch(() => { note.style.display = "block"; return false; });
}
document.querySelectorAll("button[data-k]").forEach(b => {
  const send = () => { post("key?k=" + b.dataset.k); if (navigator.vibrate) navigator.vibrate(8); };
  let timer = null, again = null;
  const stop = () => { clearTimeout(timer); clearInterval(again); };
  b.addEventListener("pointerdown", e => {
    e.preventDefault();
    send();
    if (b.hasAttribute("data-hold")) timer = setTimeout(() => { again = setInterval(send, 140); }, 450);
  });
  ["pointerup", "pointerleave", "pointercancel"].forEach(ev => b.addEventListener(ev, stop));
  b.addEventListener("contextmenu", e => e.preventDefault());
});
document.getElementById("form").addEventListener("submit", e => {
  e.preventDefault();
  const input = document.getElementById("text");
  if (!input.value) return;
  post("text", input.value).then(ok => { if (ok) input.value = ""; });
});
</script>
</body>
</html>"""
    }
}

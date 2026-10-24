package dev.evim.tv.data

/**
 * Telefonda açılan yükleme sayfası. Dışarıdan hiçbir şey yüklemez; dosyalar sırayla, ilerleme çubuğuyla
 * gönderilir. Dil telefon tarayıcısının diline göre Türkçe ya da İngilizce.
 */
internal object UploadPage {
    private val TR = mapOf(
        "lang" to "tr",
        "title" to "TV'ye dosya gönder",
        "subtitle" to "Seçtiğiniz dosyalar TV'deki İndirilenler klasörüne gelir.",
        "pick" to "Dosya seç",
        "waiting" to "Sırada",
        "done" to "Gönderildi",
        "failed" to "Gönderilemedi",
        "nospace" to "TV'de yer yok",
        "closed" to "TV'deki ekran kapanmış olabilir. Dosya al ekranını yeniden açıp QR kodu tekrar okutun.",
    )
    private val EN = mapOf(
        "lang" to "en",
        "title" to "Send files to the TV",
        "subtitle" to "The files you pick go to the Downloads folder on the TV.",
        "pick" to "Choose files",
        "waiting" to "Waiting",
        "done" to "Sent",
        "failed" to "Couldn't send",
        "nospace" to "Not enough space on the TV",
        "closed" to "The screen on the TV may have been closed. Open Receive files again and scan the QR code once more.",
    )

    fun html(turkish: Boolean): String {
        val t = if (turkish) TR else EN
        val strings = t.entries.joinToString(",") { (k, v) -> "\"$k\":\"${v.replace("\"", "\\\"")}\"" }
        return """<!doctype html>
<html lang="${t["lang"]}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${t["title"]}</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 28px 18px; background: #0b0b0f; color: #fff; font: 16px/1.4 system-ui, -apple-system, sans-serif; }
  main { max-width: 520px; margin: 0 auto; }
  .icon { width: 52px; height: 52px; border-radius: 16px; background: rgba(122,162,255,.18); display: grid; place-items: center; margin-bottom: 14px; }
  h1 { font-size: 22px; margin: 0 0 6px; }
  p { color: rgba(255,255,255,.7); margin: 0 0 22px; }
  label { display: block; text-align: center; padding: 16px; border-radius: 999px; background: #7aa2ff; color: #0b0b0f; font-weight: 700; cursor: pointer; }
  input { display: none; }
  ul { list-style: none; padding: 0; margin: 22px 0 0; }
  li { background: rgba(255,255,255,.06); border-radius: 14px; padding: 12px 14px; margin-bottom: 8px; }
  .row { display: flex; justify-content: space-between; gap: 10px; font-size: 14px; }
  .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .state { color: rgba(255,255,255,.6); flex: none; }
  .ok .state { color: #34d399; } .bad .state { color: #e5484d; }
  .bar { height: 4px; border-radius: 4px; background: rgba(255,255,255,.15); margin-top: 8px; overflow: hidden; }
  .bar div { height: 100%; width: 0; background: #7aa2ff; transition: width .2s; }
  .ok .bar div { background: #34d399; }
  .note { color: #fbbf24; font-size: 14px; margin-top: 14px; display: none; }
</style>
</head>
<body>
<main>
  <div class="icon"><svg width="26" height="26" viewBox="0 0 24 24" fill="#7aa2ff"><path d="M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-4v2h3v2H7v-2h3v-2H6a2 2 0 0 1-2-2zm2 0v10h12V5zm6 1.5 3.5 3.5H13v3h-2v-3H8.5z"/></svg></div>
  <h1>${t["title"]}</h1>
  <p>${t["subtitle"]}</p>
  <label>${t["pick"]}<input id="pick" type="file" multiple></label>
  <ul id="list"></ul>
  <div class="note" id="note">${t["closed"]}</div>
</main>
<script>
const T = {$strings};
const list = document.getElementById("list");
const queue = [];
let busy = false;
document.getElementById("pick").addEventListener("change", e => {
  for (const file of e.target.files) {
    const li = document.createElement("li");
    li.innerHTML = '<div class="row"><span class="name"></span><span class="state"></span></div><div class="bar"><div></div></div>';
    li.querySelector(".name").textContent = file.name;
    li.querySelector(".state").textContent = T.waiting;
    list.appendChild(li);
    queue.push({ file, li });
  }
  e.target.value = "";
  next();
});
function next() {
  if (busy || !queue.length) return;
  busy = true;
  const { file, li } = queue.shift();
  const state = li.querySelector(".state"), bar = li.querySelector(".bar div");
  const xhr = new XMLHttpRequest();
  xhr.open("POST", "upload?name=" + encodeURIComponent(file.name));
  xhr.upload.onprogress = e => { if (e.lengthComputable) { const p = Math.round(e.loaded * 100 / e.total); bar.style.width = p + "%"; state.textContent = T.lang === "tr" ? "%" + p : p + "%"; } };
  xhr.onload = () => finish(xhr.status === 200 ? "done" : xhr.status === 507 ? "nospace" : "failed");
  xhr.onerror = () => { finish("failed"); document.getElementById("note").style.display = "block"; };
  function finish(result) {
    li.className = result === "done" ? "ok" : "bad";
    state.textContent = T[result];
    if (result === "done") bar.style.width = "100%";
    busy = false;
    next();
  }
  xhr.send(file);
}
</script>
</body>
</html>"""
    }
}

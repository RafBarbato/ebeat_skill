# Certificazione skill ebeat — Store pubblico (AWS Lambda)

Guida operativa alla submission sulla Alexa Developer Console. Hosting: **AWS
Lambda** (endpoint = ARN). La parte tecnica (intent, AudioPlayer,
PlaybackController, no-crash, i18n IT+EN) è **pronta** — vedi
`ALEXA_INTEGRATION_SPEC.md`. Qui: metadati, compliance, test per i reviewer.

---

## ⚠️ 0. Due nodi da sciogliere PRIMA di sottomettere

### 0.1 Copyright (rischio di rigetto più alto)
La skill riproduce audio derivato da **YouTube** (URL `googlevideo`). Su skill
**pubblica** questo è esposto alla **content policy Amazon** e a questioni di
diritti musicali → causa concreta di rigetto. Il vincolo "no storage, solo URL
live" è una copertura per uso privato, non elimina il rischio sullo store.
**Decisione di business/legale**, da prendere prima di investire nell'iter.

### 0.2 Gotcha "stato vuoto" per i reviewer (bloccante se ignorato)
La skill riproduce ciò che l'**app** ha scritto in `current_track`/`playback_queue`
(su Redis). Un reviewer Amazon **non usa l'app mobile**: se apre la skill con lo
store vuoto, sente *"Nessuna traccia, avvia dall'app"* → sembra rotta/incompleta
→ **rigetto**.
**Azione obbligatoria prima del submit**: pre-popolare in **Redis (prod)** lo
stato dell'account di test (una `current_track` valida + qualche riga di
`playback_queue`) così che *"apri ebeat"* riproduca **subito**. Con Redis
effimero, ripetere se il BE/Redis si riavvia prima della review.

---

## 1. Endpoint & build
- **Endpoint**: ARN della Lambda (handler `EbeatStreamHandler`, Java 21, ≥512 MB,
  timeout ≥8 s). Consigliato **SnapStart** (gratis) per tagliare il cold start.
- **Interfacce**: abilitare **AudioPlayer**.
- **Modelli**: importare `models/it.json` e `models/en-US.json`, **Build Model**
  per ciascuna locale.
- **Env Lambda**: `SKILL_BE_INTERNAL_URL`, `SKILL_BE_SECRET`,
  `SUPABASE_DB_TRACK_URI`, `SUPABASE_SERVICE_KEY` (vedi spec §11.3/§12).

## 2. Account linking (OAuth)
- Authorization URI: `https://<host>/alexa/oauth/authorize`
- Access Token URI: `https://<host>/alexa/oauth/token`
- `client_id`/`client_secret` coerenti con `ALEXA_OAUTH_*` del BE.
- **Deve funzionare per i reviewer** con l'account di test (vedi §6).

---

## 3. Metadati Distribution (testi pronti)

> Nomi: il **public name** (card store) e l'**invocation name** (come si apre a
> voce) possono differire. **Invocation name scelto: `ebeat music`** (universale
> IT+EN — due parole col brand, evita il rischio della parola singola/generica).

### IT (it-IT)
- **Public name**: `ebeat`
- **One-sentence description**:
  `Riprendi su Alexa la musica che stai ascoltando nell'app ebeat.`
- **Detailed description**:
  `ebeat porta su Alexa la tua musica: riprendi la riproduzione dal punto esatto
   in cui l'hai lasciata nell'app, passa alla traccia successiva, metti in pausa
   o in loop, tutto a voce. Richiede l'app ebeat e il collegamento dell'account.`
- **Example phrases** (3, con invocation name):
  1. `Alexa, apri ebeat musica`
  2. `Alexa, chiedi a ebeat musica di mettere in pausa`
  3. `Alexa, chiedi a ebeat musica la prossima`

### EN (en-US)
- **Public name**: `ebeat`
- **One-sentence description**:
  `Resume on Alexa the music you're listening to in the ebeat app.`
- **Detailed description**:
  `ebeat brings your music to Alexa: resume playback from exactly where you left
   off in the app, skip to the next track, pause or loop — all by voice.
   Requires the ebeat app and account linking.`
- **Example phrases**:
  1. `Alexa, open ebeat music`
  2. `Alexa, ask ebeat music to pause`
  3. `Alexa, ask ebeat music to skip`

### Comuni
- **Category**: `Music & Audio`
- **Keywords**: `music, player, ebeat, playback, resume, streaming`
- **Icone**: **108×108** (small) + **512×512** (large), PNG, sfondo non
  trasparente. → asset da fornire (design).
- **Privacy Policy URL**: **obbligatoria** (account linking → dati personali).
  Bozza in §5, da ospitare a un URL pubblico.
- **Terms of Use URL**: opzionale (consigliata).

---

## 4. Privacy & Compliance (risposte)
- Raccoglie info personali? **Sì** — email dell'utente via account linking.
- Diretta a bambini? **No**.
- Contiene pubblicità? **No**.
- Export compliance: dichiarare secondo policy standard (nessuna crittografia
  proprietaria oltre TLS).
- Privacy policy: **richiesta** (vedi §5).

---

## 5. Bozza Privacy Policy (da ospitare, es. GitHub Pages / sito)

> **Privacy Policy — ebeat Alexa skill**
>
> La skill ebeat consente di riprodurre su Alexa la musica associata al tuo
> account ebeat.
>
> **Dati trattati**: al collegamento dell'account (account linking) la skill
> riceve un token con cui il nostro backend risolve la tua **email**, usata come
> identificativo per recuperare lo stato di riproduzione (traccia corrente, coda,
> posizione). Non raccogliamo contatti, pagamenti né dati di navigazione tramite
> la skill.
>
> **Uso**: i dati servono esclusivamente a sincronizzare la riproduzione tra
> l'app ebeat e Alexa. Non vengono venduti né condivisi con terzi per marketing.
>
> **Conservazione**: lo stato di riproduzione è tenuto in memoria transitoria sul
> nostro backend e scade automaticamente. Nessun file audio viene memorizzato.
>
> **Diritti**: puoi scollegare l'account in ogni momento dall'app Alexa, cosa che
> interrompe l'accesso della skill ai tuoi dati.
>
> **Contatti**: `<support-email>`.
>
> Ultimo aggiornamento: `<data>`.

---

## 6. Testing instructions per i reviewer (campo "Testing Instructions")

> This skill resumes the music the user is playing in the companion **ebeat**
> mobile app, on an Alexa device with the **AudioPlayer** interface.
>
> **Account linking is required.** Please use the test account below.
>
> 1. In the Alexa app: Skills → ebeat → **Link Account** → sign in with:
>    - email: `<test-email>`
>    - password: `<test-password>`
> 2. Say **"Alexa, open ebeat music"** → the skill starts playing a track.
> 3. Controls: "Alexa, pause" / "Alexa, resume" / "Alexa, next" /
>    "Alexa, loop" / "Alexa, stop". Playback card buttons (play/pause/next)
>    also work.
>
> The test account has been pre-loaded with a current track and a queue, so
> playback starts immediately after linking — no mobile app action needed.

⚠️ Ricorda §0.2: **pre-carica lo stato del test account in Redis prod** prima di
inviare, o il punto 2 fallisce.

---

## 7. Da decidere / input necessari da te
- [ ] **Copyright** (§0.1) — via libera o ripiego su distribuzione privata/beta?
- [x] **Invocation name**: `ebeat music` (universale IT+EN) — impostato nei
      `models/*.json`. Ricorda il **Build Model** in Console.
- [ ] **Public name** definitivo (proposto: `ebeat`).
- [ ] **support-email** per privacy policy e contatto publisher.
- [ ] **URL hosting** della privacy policy.
- [ ] **Icone** 108/512 (design).
- [ ] **Account di test** dedicato (email/password) + suo stato pre-caricato in
      Redis prod.

## 8. Checklist submit
- [ ] Endpoint = ARN Lambda; SnapStart on; AudioPlayer on.
- [ ] Modelli it-IT + en-US buildati.
- [ ] Account linking testato end-to-end col test account.
- [ ] Stato test account pre-caricato in Redis prod (§0.2).
- [ ] Metadati IT+EN, icone, privacy URL, testing instructions compilati.
- [ ] Compliance (§4) compilata.
- [ ] Nodo copyright (§0.1) affrontato.

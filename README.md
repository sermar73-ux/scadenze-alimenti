# Scadenze alimenti

App Android per tenere d'occhio le scadenze di frigo, freezer e dispensa e la lista della spesa.
È la stessa pagina del sito (`app/src/main/assets/index.html`) dentro un'app vera, con in più:

- **scanner dei codici a barre e lettura della data dalla foto fatti sul telefono** (ML Kit), anche senza internet;
- **avvisi di scadenza ogni mattina verso le 9, anche ad app chiusa**;
- copia dei dati condivisibile (Drive, File, WhatsApp…) e ripristinabile, con promemoria mensile;
- widget per la schermata Home con gli alimenti che scadono per primi;
- lista della spesa ricevuta direttamente dalla Tavola di Ottobre.
- foto dei prodotti ingrandibili: tocca la miniatura (con la lente) per vederla a schermo intero, con zoom a due dita;
  toccando il resto della riga si apre, come prima, la scheda della scadenza.
- **Cucina**: scegli gli ingredienti di casa e Claude propone le ricette e le spiega passo passo,
  con timer che suonano anche a schermo spento e ricettario offline.

## Cucina con Claude

La scheda Cucina c'è solo nell'app (non su `sito/index.html`) e chiama direttamente l'API di Claude.
La chiave si inserisce in ⚙ Impostazioni → Cucina con Claude: resta nel telefono, in
`localStorage` sotto `cucina.chiave.v1`, fuori da `casa.v1` e quindi fuori dalle copie dei dati.
Non va mai scritta nel codice: questo repository è pubblico. Per lo stesso motivo le preferenze
personali (dieta, cibi esclusi) stanno solo nelle Impostazioni dell'app.

## Scaricare l'APK

Dal telefono apri questo link con Chrome e installa il file:

https://github.com/sermar73-ux/scadenze-alimenti/releases/latest/download/Scadenze.apk

Ogni push avvia il workflow **Build APK** (`.github/workflows/build.yml`), che compila
l'app e la pubblica come nuova Release.

## Passare i dati dal sito all'app

I dati del sito restano nel browser. Sul sito: ⚙ Impostazioni → Copia dei dati → **Salva una copia**.
Nell'app: ⚙ Impostazioni → **Ripristina da una copia** → scegli quel file.

## Lista della spesa dalla Tavola di Ottobre

Il pulsante "Aggiungi a Scadenze alimenti" della Tavola apre `scadenze://spesa?l=<lista>`:
se l'app è installata la lista arriva qui, altrimenti si apre il sito
`https://dynamic-lollipop-3b2529.netlify.app/#spesa=…` come prima.

L'app sa aprire anche i link del sito: Impostazioni del telefono → App → Scadenze →
**Apri in modalità predefinita** → "Apri i collegamenti supportati" → aggiungi l'indirizzo del sito.

## Sito su Netlify

`sito/index.html` è la versione per il sito delle Scadenze (`dynamic-lollipop-3b2529.netlify.app`).
Su Netlify: progetto dynamic-lollipop → Deploys → scarica l'ultimo deploy, sostituisci
`index.html` con questo e trascina tutta la cartella in Deploys.

## Aggiornare la pagina

Sostituisci `app/src/main/assets/index.html` con la nuova versione scaricata dal sito e fai push.
`app/src/main/assets/android.js` collega la pagina al telefono (notifiche, scanner, condivisione)
senza bisogno di modificarla.

## Compilare in locale

```
./gradlew assembleDebug
```

L'APK finisce in `app/build/outputs/apk/debug/app-debug.apk`.

# Scadenze alimenti

App Android per tenere d'occhio le scadenze di frigo e dispensa e la lista della spesa.
È la stessa pagina del sito (`app/src/main/assets/index.html`) dentro un'app vera, con in più:

- **scanner dei codici a barre e lettura della data dalla foto fatti sul telefono** (ML Kit), anche senza internet;
- **avvisi di scadenza ogni mattina verso le 9, anche ad app chiusa**;
- copia dei dati condivisibile (Drive, File, WhatsApp…) e ripristinabile;
- apertura diretta dei link della spesa mandati dalla Tavola di Ottobre.

## Scaricare l'APK

Dal telefono apri questo link con Chrome e installa il file:

https://github.com/sermar73-ux/scadenze-alimenti/releases/latest/download/Scadenze.apk

Ogni push avvia il workflow **Build APK** (`.github/workflows/build.yml`), che compila
l'app e la pubblica come nuova Release.

## Passare i dati dal sito all'app

I dati del sito restano nel browser. Sul sito: ⚙ Impostazioni → Copia dei dati → **Salva una copia**.
Nell'app: ⚙ Impostazioni → **Ripristina da una copia** → scegli quel file.

## Link della spesa dalla Tavola di Ottobre

Perché i link `https://enchanting-crumble-68c6ce.netlify.app/#spesa=…` si aprano nell'app e non nel browser:
Impostazioni del telefono → App → Scadenze → **Apri per impostazione predefinita** → attiva
"Apri link supportati" e aggiungi l'indirizzo del sito.

## Aggiornare la pagina

Sostituisci `app/src/main/assets/index.html` con la nuova versione scaricata dal sito e fai push.
`app/src/main/assets/android.js` collega la pagina al telefono (notifiche, scanner, condivisione)
senza bisogno di modificarla.

## Compilare in locale

```
./gradlew assembleDebug
```

L'APK finisce in `app/build/outputs/apk/debug/app-debug.apk`.

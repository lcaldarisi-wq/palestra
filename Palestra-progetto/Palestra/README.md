# Palestra – app Android per gli allenamenti

App personale, completamente offline, per creare schede, registrare gli allenamenti con timer di recupero reale e seguire progressi e record.
Kotlin + Jetpack Compose (Material 3), database Room. Colori: giallo `#FFBE0B` (primario) e marrone `#2A2312` (secondario) su fondo scuro.

---

## 1. Ottenere l'APK

### Opzione A – GitHub (senza installare nulla sul PC)
1. Crea un account su <https://github.com> (gratuito) e poi un **nuovo repository privato** (es. `palestra`).
2. Nella pagina del repository: **Add file → Upload files**, trascina **tutto il contenuto** della cartella `Palestra` (comprese le cartelle nascoste `.github` e `gradle`) e conferma con **Commit changes**.
   - Se il browser non carica le cartelle nascoste, usa GitHub Desktop oppure carica a mano il file `.github/workflows/build.yml` (Add file → Create new file, nome `.github/workflows/build.yml`, incolla il contenuto).
3. Apri la scheda **Actions**: parte da sola la compilazione "Compila APK" (5–8 minuti).
4. A compilazione finita (spunta verde) apri l'esecuzione e scarica l'artefatto **Palestra-APK** (è uno zip che contiene `Palestra.apk`).
5. Se compare una ❌ rossa, apri l'esecuzione, copia le righe con `e:` o `error` e inviale a Claude: verranno corrette.

### Opzione B – Android Studio sul PC
1. Installa Android Studio (<https://developer.android.com/studio>).
2. **File → Open** e scegli la cartella `Palestra`. Attendi la sincronizzazione di Gradle (la prima volta scarica i componenti).
3. Collega il telefono con il cavo USB (con *Debug USB* attivo nelle Opzioni sviluppatore) e premi ▶ **Run**,
   oppure **Build → Build App Bundle(s)/APK(s) → Build APK(s)**: l'APK si trova in `app/build/outputs/apk/`.

## 2. Installare sul telefono
1. Copia `Palestra.apk` sul telefono (Drive, cavo, e-mail…) e aprilo.
2. Se richiesto, consenti **Installa app sconosciute** per l'app da cui lo apri (File, Chrome, Drive…).
3. Al primo avvio concedi le **notifiche**: servono per il timer a schermo spento.

**Aggiornamenti:** l'APK è firmato con una chiave fissa inclusa nel progetto (`app/palestra.keystore`), quindi ogni nuova versione si installa sopra la precedente **senza perdere i dati**. Non eliminare né sostituire questo file.

## 3. Cambio telefono
- **Impostazioni → Backup e cambio telefono → Esporta**: crea un file `.json` con schede, allenamenti ed esercizi personalizzati. Salvalo su Drive, sul PC o invialo via e-mail.
- Sul nuovo telefono: installa l'APK e usa **Importa** su quel file.
- In più è attivo il backup automatico di Android (database e impostazioni), se abilitato sul tuo account Google.

---

## Funzionalità
- **Schede**: più schede con nome, esercizi riordinabili, serie / ripetizioni / peso / recupero, serie di riscaldamento, superserie, timer automatico per esercizio; assegnazione ai giorni della settimana.
- **Catalogo**: 146 esercizi (elenco del PDF senza doppioni + Addome, Avambracci, Cardio e altri esercizi comuni), ricerca, filtri per gruppo, esercizi personalizzati con indicazioni di esecuzione, archiviazione.
- **Allenamento**: esercizio corrente con indicazioni, valori dell'ultima volta, peso e ripetizioni modificabili, completamento con un tocco, serie **di riscaldamento (R)** e **fallite (F)**, aggiunta/eliminazione serie, cambio esercizio rapido, evidenza dei nuovi record, salvataggio immediato di ogni tocco (si riprende esattamente da dove si era), schermo sempre acceso.
- **Timer di recupero**: avvio automatico, 60/90/120/180 s o personalizzato, pausa / riprendi / ±15 s / salta, suono e vibrazione, notifica con conto alla rovescia, barra visibile in tutte le schermate.
- **Calendario**: vista mensile e settimanale, allenamenti completati / programmati / saltati, sposta, recupera, salta, pianifica un giorno libero.
- **Statistiche**: riepilogo settimanale e mensile con confronto col periodo precedente, frequenza, grafico per esercizio (peso max, 1RM stimato, volume, ripetizioni, durata, distanza), record personali, confronto tra le ultime due sessioni, serie per gruppo muscolare.
- **Storico**: ricerca per esercizio o scheda, filtri per periodo, data e scheda; dettaglio con confronto rispetto alla sessione precedente; modifica, ripeti, cambia data, elimina.

### Regole di calcolo
- Volume = peso × ripetizioni delle serie completate.
- Le serie di **riscaldamento** non contano né nel volume né nei record.
- Le serie **fallite** contano nel volume e nel peso massimo, ma non nel record di ripetizioni.
- Le statistiche sono calcolate al volo dalle serie registrate: modificando una sessione passata tutto si aggiorna.

## Scelte tecniche
| Ambito | Scelta | Motivo |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Nativo, moderno, tema scuro coerente |
| Dati | Room (SQLite) | Offline, affidabile, migrazioni dello schema per le versioni future (`app/schemas`) |
| Architettura | MVVM + repository, DI manuale (`AppContainer`) | Codice ordinato senza generatori di codice aggiuntivi |
| Timer | Istante di fine salvato + allarme esatto di sistema (AlarmManager) + notifica con countdown | Funziona a schermo spento, in background e anche se Android chiude l'app; resiste ai cambi d'ora |
| Grafici | Disegnati con Canvas di Compose | Nessuna libreria esterna, stile coerente |
| Migrazione | Backup JSON leggibile + backup automatico Android | Nessun account necessario |

## Struttura del codice
```
app/src/main/java/it/palestra/app/
├── PalestraApp.kt          Application + AppContainer (dipendenze)
├── MainActivity.kt
├── data/
│   ├── db/                 Entità, DAO, database Room
│   ├── Repositories.kt     Esercizi, schede/programmazione, sessioni
│   ├── Stats.kt            Volume, record, progressi (logica pura, testata)
│   ├── Planner.kt          Programmazione e stato dei giorni (testata)
│   ├── ExerciseCatalog.kt  Catalogo iniziale
│   ├── BackupManager.kt    Esporta / importa JSON
│   └── SettingsRepository.kt
├── timer/RestTimer.kt      Controller, allarme, notifiche, receiver
└── ui/                     Schermate Compose (today, templates, workout, calendar, stats, history, settings, components)
app/src/test/               Test unitari (statistiche, record, programmazione, catalogo)
```

Test: `./gradlew testDebugUnitTest`

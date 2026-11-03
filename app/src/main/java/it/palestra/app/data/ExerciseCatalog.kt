package it.palestra.app.data

import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.ExerciseType.BODYWEIGHT
import it.palestra.app.data.db.ExerciseType.CARDIO
import it.palestra.app.data.db.ExerciseType.TIME
import it.palestra.app.data.db.ExerciseType.WEIGHT
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.data.db.MuscleGroup.ADDOME
import it.palestra.app.data.db.MuscleGroup.AVAMBRACCI
import it.palestra.app.data.db.MuscleGroup.BICIPITI
import it.palestra.app.data.db.MuscleGroup.DORSO
import it.palestra.app.data.db.MuscleGroup.GAMBE
import it.palestra.app.data.db.MuscleGroup.PETTO
import it.palestra.app.data.db.MuscleGroup.SPALLE
import it.palestra.app.data.db.MuscleGroup.TRICIPITI

/**
 * Catalogo iniziale degli esercizi.
 * Base: "Elenco esercizi da sala pesi" (PDF fornito), senza doppioni.
 * Le voci marcate "// +" sono integrazioni aggiunte (gruppi Addome, Avambracci, Cardio e
 * alcuni esercizi comuni mancanti).
 */
object ExerciseCatalog {

    private fun e(
        name: String,
        group: MuscleGroup,
        equipment: String,
        type: ExerciseType = if (equipment.equals("Corpo libero", ignoreCase = true)) BODYWEIGHT else WEIGHT,
        secondary: MuscleGroup? = null,
    ) = ExerciseEntity(
        name = name,
        muscleGroup = group,
        secondaryGroup = secondary?.name,
        equipment = equipment,
        type = type,
    )

    val items: List<ExerciseEntity> = listOf(
        // DORSO
        e("Lat machine avanti", DORSO, "Macchina"),
        e("Lat machine presa inversa", DORSO, "Macchina", secondary = BICIPITI),
        e("Lat machine presa stretta", DORSO, "Macchina"),
        e("Trazioni alla sbarra", DORSO, "Corpo libero", secondary = BICIPITI),
        e("Chin-up", DORSO, "Corpo libero", secondary = BICIPITI),
        e("Rematore con bilanciere", DORSO, "Bilanciere"),
        e("Rematore con manubrio", DORSO, "Manubrio"),
        e("Rematore T-bar", DORSO, "T-bar"),
        e("Rematore alla macchina", DORSO, "Macchina"),
        e("Rematore al cavo basso / pulley", DORSO, "Cavi"),
        e("Pullover ai cavi", DORSO, "Cavi"),
        e("Pullover con manubrio", DORSO, "Manubrio", secondary = PETTO),
        e("Stacco da terra", DORSO, "Bilanciere", secondary = GAMBE),
        e("Iperestensioni lombari", DORSO, "Panca romana"),
        e("Good morning", DORSO, "Bilanciere", secondary = GAMBE),
        e("Pulldown a braccia tese ai cavi", DORSO, "Cavi"), // +
        e("Rematore Pendlay", DORSO, "Bilanciere"), // +
        e("Rack pull", DORSO, "Bilanciere"), // +
        e("Lat machine unilaterale", DORSO, "Cavi"), // +
        e("Trazioni assistite", DORSO, "Macchina", secondary = BICIPITI), // +
        e("Rematore inverso", DORSO, "Corpo libero"), // +

        // SPALLE
        e("Shoulder press", SPALLE, "Macchina"),
        e("Military press", SPALLE, "Bilanciere"),
        e("Arnold press", SPALLE, "Manubri"),
        e("Lento avanti", SPALLE, "Bilanciere"),
        e("Lento con manubri", SPALLE, "Manubri"),
        e("Alzate laterali", SPALLE, "Manubri"),
        e("Alzate laterali ai cavi", SPALLE, "Cavi"),
        e("Alzate laterali alla macchina", SPALLE, "Macchina"),
        e("Alzate frontali", SPALLE, "Manubri"),
        e("Alzate frontali al cavo", SPALLE, "Cavi"),
        e("Face pull", SPALLE, "Cavi", secondary = DORSO),
        e("Reverse pec deck", SPALLE, "Macchina"),
        e("Croci inverse ai cavi", SPALLE, "Cavi"),
        e("Scrollate (shrugs)", SPALLE, "Manubri o bilanciere"),
        e("Rematore al mento", SPALLE, "Bilanciere o cavi"),
        e("Landmine press", SPALLE, "Bilanciere"), // +
        e("Alzate a Y su panca inclinata", SPALLE, "Manubri"), // +
        e("Shoulder press alla Smith machine", SPALLE, "Smith machine"), // +

        // PETTO
        e("Panca piana", PETTO, "Bilanciere", secondary = TRICIPITI),
        e("Panca inclinata", PETTO, "Bilanciere"),
        e("Panca declinata", PETTO, "Bilanciere"),
        e("Panca piana con manubri", PETTO, "Manubri"),
        e("Panca inclinata con manubri", PETTO, "Manubri"),
        e("Panca declinata con manubri", PETTO, "Manubri"),
        e("Chest press", PETTO, "Macchina"),
        e("Chest press inclinata", PETTO, "Macchina"),
        e("Pec deck", PETTO, "Macchina"),
        e("Croci ai cavi", PETTO, "Cavi"),
        e("Croci ai cavi dall'alto", PETTO, "Cavi"),
        e("Croci ai cavi dal basso", PETTO, "Cavi"),
        e("Croci con manubri", PETTO, "Manubri"),
        e("Dip alle parallele", PETTO, "Corpo libero", secondary = TRICIPITI),
        e("Push-up", PETTO, "Corpo libero", secondary = TRICIPITI),
        e("Push-up inclinati", PETTO, "Corpo libero"),
        e("Push-up declinati", PETTO, "Corpo libero"),
        e("Panca piana alla Smith machine", PETTO, "Smith machine"), // +
        e("Panca inclinata alla Smith machine", PETTO, "Smith machine"), // +
        e("Svend press", PETTO, "Disco"), // +

        // GAMBE E GLUTEI
        e("Squat", GAMBE, "Bilanciere"),
        e("Front squat", GAMBE, "Bilanciere"),
        e("Goblet squat", GAMBE, "Manubrio/kettlebell"),
        e("Hack squat", GAMBE, "Macchina"),
        e("Leg press", GAMBE, "Macchina"),
        e("Bulgarian split squat", GAMBE, "Manubri/bilanciere"),
        e("Affondi in camminata", GAMBE, "Manubri"),
        e("Affondi indietro", GAMBE, "Manubri"),
        e("Step-up", GAMBE, "Manubri/corpo libero"),
        e("Leg extension", GAMBE, "Macchina"),
        e("Leg curl sdraiato", GAMBE, "Macchina"),
        e("Leg curl seduto", GAMBE, "Macchina"),
        e("Stacco rumeno", GAMBE, "Bilanciere/manubri", secondary = DORSO),
        e("Stacco sumo", GAMBE, "Bilanciere"),
        e("Hip thrust", GAMBE, "Bilanciere/macchina"),
        e("Glute bridge", GAMBE, "Bilanciere/corpo libero"),
        e("Abduzioni dell'anca", GAMBE, "Macchina/cavi"),
        e("Adduzioni dell'anca", GAMBE, "Macchina"),
        e("Calf raise in piedi", GAMBE, "Macchina"),
        e("Calf raise seduto", GAMBE, "Macchina"),
        e("Calf raise alla leg press", GAMBE, "Macchina"),
        e("Nordic curl", GAMBE, "Corpo libero"),
        e("Back extension", GAMBE, "Panca romana", secondary = DORSO),
        e("Squat alla Smith machine", GAMBE, "Smith machine"), // +
        e("Pendulum squat", GAMBE, "Macchina"), // +
        e("Sissy squat", GAMBE, "Corpo libero"), // +
        e("Affondi laterali", GAMBE, "Manubri"), // +
        e("Kickback glutei ai cavi", GAMBE, "Cavi"), // +
        e("Pull-through ai cavi", GAMBE, "Cavi"), // +
        e("Stacco con trap bar", GAMBE, "Trap bar", secondary = DORSO), // +
        e("Wall sit", GAMBE, "Corpo libero", type = TIME), // +

        // BICIPITI
        e("Curl con bilanciere", BICIPITI, "Bilanciere"),
        e("Curl con bilanciere EZ", BICIPITI, "EZ"),
        e("Curl con manubri", BICIPITI, "Manubri"),
        e("Curl alternato", BICIPITI, "Manubri"),
        e("Curl a martello (hammer curl)", BICIPITI, "Manubri", secondary = AVAMBRACCI),
        e("Curl concentrato", BICIPITI, "Manubrio"),
        e("Curl su panca Scott", BICIPITI, "EZ/manubri"),
        e("Curl ai cavi", BICIPITI, "Cavi"),
        e("Curl al cavo basso con barra", BICIPITI, "Cavi"),
        e("Curl ai cavi con corda", BICIPITI, "Cavi"),
        e("Curl su panca inclinata", BICIPITI, "Manubri"),
        e("Curl spider", BICIPITI, "Manubri/bilanciere"),
        e("Reverse curl", BICIPITI, "EZ/manubri", secondary = AVAMBRACCI),
        e("Curl bayesiano ai cavi", BICIPITI, "Cavi"), // +
        e("Curl alla macchina", BICIPITI, "Macchina"), // +
        e("Drag curl", BICIPITI, "Bilanciere"), // +

        // TRICIPITI
        e("Pushdown al cavo", TRICIPITI, "Cavi"),
        e("Pushdown con corda", TRICIPITI, "Cavi"),
        e("Pushdown con barra", TRICIPITI, "Cavi"),
        e("French press", TRICIPITI, "EZ/manubri"),
        e("Skull crusher", TRICIPITI, "EZ"),
        e("Estensioni sopra la testa", TRICIPITI, "Manubrio/cavo"),
        e("Kickback", TRICIPITI, "Manubri/cavi"),
        e("Dip alla macchina", TRICIPITI, "Macchina"),
        e("Panca presa stretta", TRICIPITI, "Bilanciere", secondary = PETTO),
        e("Estensioni tricipiti ai cavi unilaterali", TRICIPITI, "Cavi"),
        e("JM press", TRICIPITI, "Bilanciere"), // +
        e("Estensioni tricipiti alla macchina", TRICIPITI, "Macchina"), // +
        e("Push-up a presa stretta (diamond)", TRICIPITI, "Corpo libero"), // +
        e("Dip tra panche", TRICIPITI, "Corpo libero"), // +

        // ADDOME (+)
        e("Crunch", ADDOME, "Corpo libero"),
        e("Crunch inverso", ADDOME, "Corpo libero"),
        e("Crunch ai cavi", ADDOME, "Cavi"),
        e("Crunch alla macchina", ADDOME, "Macchina"),
        e("Plank", ADDOME, "Corpo libero", type = TIME),
        e("Plank laterale", ADDOME, "Corpo libero", type = TIME),
        e("Hollow hold", ADDOME, "Corpo libero", type = TIME),
        e("Leg raise alla sbarra", ADDOME, "Corpo libero"),
        e("Leg raise a terra", ADDOME, "Corpo libero"),
        e("Ab wheel rollout", ADDOME, "Ruota addominale", type = BODYWEIGHT),
        e("Russian twist", ADDOME, "Disco/manubrio"),
        e("Pallof press", ADDOME, "Cavi"),
        e("Dead bug", ADDOME, "Corpo libero"),
        e("Mountain climber", ADDOME, "Corpo libero", type = TIME),

        // AVAMBRACCI (+)
        e("Wrist curl", AVAMBRACCI, "Bilanciere/manubri"),
        e("Reverse wrist curl", AVAMBRACCI, "Bilanciere/manubri"),
        e("Farmer's walk", AVAMBRACCI, "Manubri", type = TIME),
        e("Dead hang alla sbarra", AVAMBRACCI, "Sbarra", type = TIME),

        // CARDIO (+)
        e("Tapis roulant", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Camminata in salita (tapis roulant)", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Cyclette", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Ellittica", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Vogatore", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Stair climber", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Assault bike", MuscleGroup.CARDIO, "Macchina", type = CARDIO),
        e("Salto della corda", MuscleGroup.CARDIO, "Corda", type = CARDIO),
    )
}

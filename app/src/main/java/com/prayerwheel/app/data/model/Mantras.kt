package com.prayerwheel.app.data.model

/**
 * Collection of built-in mantras for the prayer wheel.
 */
object Mantras {

    /**
     * The most common and beloved mantra in Tibetan Buddhism.
     * Meaning: "Jewel in the Lotus"
     */
    val OM_MANI_PADME_HUM = Mantra(
        id = "om_mani_padme_hum",
        displayName = "Om Mani Padme Hum",
        tibetan = "ཨོཾ་མ་ཎི་པདྨེ་ཧཱུྃ",
        romanized = "Om Mani Padme Hum",
        meaning = "Jewel in the Lotus"
    )

    /**
     * The Vajra Guru Mantra, also known as the Padmasambhava Mantra.
     * Used for protection and blessings.
     */
    val VAJRA_GURU = Mantra(
        id = "vajra_guru",
        displayName = "Vajra Guru",
        tibetan = "བཅོམ་ལྡིང་རྗེ་བཙུན་ནོར་བུ་ཚེ་དྲུ་བཀོད་ལས",
        romanized = "Vajra Guru",
        meaning = "Vajra Guru Mantra"
    )

    /**
     * The Arapacana mantra — the twenty-five holy names of Bhaisajyaguru (the Medicine Buddha).
     */
    val ARAPACANA = Mantra(
        id = "arapacana",
        displayName = "Arapacana",
        tibetan = "ཨ་ར་པ་ཙ་ན།",
        romanized = "Arapacana",
        meaning = "The Twenty-Five Holy Names of Bhaisajyaguru"
    )

    /**
     * All built-in mantras as a list.
     */
    val ALL: List<Mantra> = listOf(
        OM_MANI_PADME_HUM,
        VAJRA_GURU,
        ARAPACANA
    )

    /**
     * Finds a mantra by its ID.
     */
    fun byId(id: String): Mantra? = ALL.find { it.id == id }
}
package net.zomis.games.impl.alchemists.Artifacts

object PrintingPress : Artifact {
    override val name: String
        get() = "Printing Press"
    override val description: String
        get() = "You do not pay 1 gold to the bank when you publish or endorse a theory."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 4
    override val points: Int
        get() = 2
}
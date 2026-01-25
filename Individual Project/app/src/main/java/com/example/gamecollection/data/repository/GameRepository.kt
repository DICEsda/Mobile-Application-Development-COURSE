package com.example.gamecollection.data.repository

import com.example.gamecollection.data.model.Category
import com.example.gamecollection.data.model.Game

/**
 * Repository class that provides access to game collection data.
 * This acts as a single source of truth for all game-related data.
 * Data is hardcoded for demonstration purposes.
 */
object GameRepository {

    // Predefined categories for the game collection
    private val categories = listOf(
        Category(
            id = "action",
            name = "Action Games",
            description = "Fast-paced games with combat and adventure",
            iconName = "sports_esports"
        ),
        Category(
            id = "rpg",
            name = "RPG Games",
            description = "Role-playing games with rich stories and character development",
            iconName = "auto_stories"
        ),
        Category(
            id = "strategy",
            name = "Strategy Games",
            description = "Games that require tactical thinking and planning",
            iconName = "psychology"
        ),
        Category(
            id = "sports",
            name = "Sports Games",
            description = "Virtual sports and racing experiences",
            iconName = "sports_soccer"
        ),
        Category(
            id = "indie",
            name = "Indie Games",
            description = "Creative games from independent developers",
            iconName = "lightbulb"
        )
    )

    // Hardcoded game data organized by category
    private val games = listOf(
        // Action Games
        Game(
            id = "action_1",
            title = "Shadow Warrior Chronicles",
            categoryId = "action",
            developer = "Phantom Studios",
            publisher = "Digital Dreams Inc.",
            releaseYear = 2023,
            genre = "Action-Adventure",
            platform = "PC, PlayStation 5, Xbox Series X",
            rating = 4.5f,
            description = "Embark on an epic journey as a legendary shadow warrior. Battle through ancient temples, defeat mythical creatures, and uncover the secrets of a forgotten civilization. Features stunning graphics, fluid combat mechanics, and an engaging story that spans across multiple realms.",
            imageUrl = "shadow_warrior",
            features = listOf("Open World", "RPG Elements", "Co-op Mode", "New Game+", "Photo Mode"),
            minPlayers = 1,
            maxPlayers = 4,
            isMultiplayer = true,
            price = 59.99
        ),
        Game(
            id = "action_2",
            title = "Neon Assault",
            categoryId = "action",
            developer = "CyberForge Games",
            publisher = "Future Play",
            releaseYear = 2024,
            genre = "First-Person Shooter",
            platform = "PC, PlayStation 5",
            rating = 4.2f,
            description = "In a dystopian cyberpunk city, you are the last hope against a rogue AI. Use high-tech weapons, hack enemy systems, and navigate through neon-lit streets in this adrenaline-pumping shooter.",
            imageUrl = "neon_assault",
            features = listOf("Cyberpunk Setting", "Weapon Customization", "Hacking Mechanics", "Multiplayer Arena"),
            minPlayers = 1,
            maxPlayers = 16,
            isMultiplayer = true,
            price = 49.99
        ),
        Game(
            id = "action_3",
            title = "Viking Rage",
            categoryId = "action",
            developer = "Norse Interactive",
            publisher = "Thunder Games",
            releaseYear = 2022,
            genre = "Hack and Slash",
            platform = "PC, Xbox Series X, Nintendo Switch",
            rating = 4.0f,
            description = "Lead your Viking clan to glory! Raid villages, conquer kingdoms, and build your legend in this brutal action game set in the age of Vikings. Features visceral combat and Norse mythology.",
            imageUrl = "viking_rage",
            features = listOf("Brutal Combat", "Clan Management", "Raid System", "Mythological Bosses"),
            minPlayers = 1,
            maxPlayers = 2,
            isMultiplayer = true,
            price = 39.99
        ),

        // RPG Games
        Game(
            id = "rpg_1",
            title = "Realm of Eternia",
            categoryId = "rpg",
            developer = "Mystic Arts Studio",
            publisher = "Epic Game Publishers",
            releaseYear = 2023,
            genre = "Open World RPG",
            platform = "PC, PlayStation 5, Xbox Series X",
            rating = 4.8f,
            description = "Explore a vast fantasy world filled with magic, dragons, and ancient mysteries. Create your hero, choose your path, and shape the fate of Eternia. Over 200 hours of content with multiple endings based on your choices.",
            imageUrl = "realm_eternia",
            features = listOf("Open World", "Character Creation", "Multiple Endings", "Crafting System", "Mount System", "Housing"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 69.99
        ),
        Game(
            id = "rpg_2",
            title = "Stellar Odyssey",
            categoryId = "rpg",
            developer = "Cosmos Entertainment",
            publisher = "Galactic Games",
            releaseYear = 2024,
            genre = "Sci-Fi RPG",
            platform = "PC, PlayStation 5",
            rating = 4.6f,
            description = "Travel across the galaxy, recruit alien companions, and make decisions that affect entire star systems. A space opera RPG with deep narrative choices and tactical combat.",
            imageUrl = "stellar_odyssey",
            features = listOf("Space Exploration", "Companion System", "Ship Customization", "Faction Reputation", "Romance Options"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 59.99
        ),
        Game(
            id = "rpg_3",
            title = "Dragon's Legacy",
            categoryId = "rpg",
            developer = "Ancient Forge",
            publisher = "Legend Games",
            releaseYear = 2021,
            genre = "Classic RPG",
            platform = "PC, Nintendo Switch",
            rating = 4.3f,
            description = "A love letter to classic RPGs. Turn-based combat, pixel art graphics, and a heartfelt story about friendship and destiny. Perfect for fans of retro gaming.",
            imageUrl = "dragons_legacy",
            features = listOf("Turn-Based Combat", "Pixel Art", "Job System", "Secret Dungeons", "New Game+"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 29.99
        ),

        // Strategy Games
        Game(
            id = "strategy_1",
            title = "Empire Architect",
            categoryId = "strategy",
            developer = "Grand Strategy Labs",
            publisher = "Tactical Minds",
            releaseYear = 2023,
            genre = "4X Strategy",
            platform = "PC",
            rating = 4.7f,
            description = "Build your empire from a small settlement to a galactic superpower. Manage resources, research technologies, wage wars, and forge alliances in this deep 4X strategy game.",
            imageUrl = "empire_architect",
            features = listOf("Empire Building", "Tech Tree", "Diplomacy System", "Custom Civilizations", "Mod Support"),
            minPlayers = 1,
            maxPlayers = 8,
            isMultiplayer = true,
            price = 49.99
        ),
        Game(
            id = "strategy_2",
            title = "Tactical Commanders",
            categoryId = "strategy",
            developer = "War Room Studios",
            publisher = "Strategic Entertainment",
            releaseYear = 2022,
            genre = "Turn-Based Tactics",
            platform = "PC, Nintendo Switch",
            rating = 4.4f,
            description = "Command elite squads in challenging turn-based missions. Every decision matters in this tactical game where positioning, cover, and teamwork determine victory.",
            imageUrl = "tactical_commanders",
            features = listOf("Squad Management", "Permadeath Mode", "Base Building", "Character Classes", "Campaign Editor"),
            minPlayers = 1,
            maxPlayers = 2,
            isMultiplayer = true,
            price = 44.99
        ),
        Game(
            id = "strategy_3",
            title = "City Tycoon Pro",
            categoryId = "strategy",
            developer = "Urban Dreams",
            publisher = "Simulation World",
            releaseYear = 2024,
            genre = "City Builder",
            platform = "PC, PlayStation 5, Xbox Series X",
            rating = 4.1f,
            description = "Design and manage your dream city. Balance residential, commercial, and industrial zones while keeping citizens happy. Features realistic traffic simulation and disaster scenarios.",
            imageUrl = "city_tycoon",
            features = listOf("City Building", "Traffic Management", "Disaster Scenarios", "Day/Night Cycle", "Workshop Support"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 39.99
        ),

        // Sports Games
        Game(
            id = "sports_1",
            title = "Ultimate Football 2024",
            categoryId = "sports",
            developer = "Sports Interactive",
            publisher = "Athletic Games",
            releaseYear = 2024,
            genre = "Football Simulation",
            platform = "PC, PlayStation 5, Xbox Series X",
            rating = 4.0f,
            description = "The most realistic football simulation ever created. Features licensed teams, stadiums, and players. New career mode lets you take a team from the lower leagues to championship glory.",
            imageUrl = "ultimate_football",
            features = listOf("Licensed Teams", "Career Mode", "Online Seasons", "Ultimate Team", "Women's Football"),
            minPlayers = 1,
            maxPlayers = 4,
            isMultiplayer = true,
            price = 69.99
        ),
        Game(
            id = "sports_2",
            title = "Velocity Racers",
            categoryId = "sports",
            developer = "Speed Demons Studio",
            publisher = "Turbo Games",
            releaseYear = 2023,
            genre = "Racing",
            platform = "PC, PlayStation 5, Xbox Series X",
            rating = 4.5f,
            description = "Feel the rush of high-speed racing across exotic locations worldwide. Features 100+ licensed cars, dynamic weather, and a comprehensive career mode.",
            imageUrl = "velocity_racers",
            features = listOf("100+ Cars", "Dynamic Weather", "VR Support", "Livery Editor", "Online Racing"),
            minPlayers = 1,
            maxPlayers = 20,
            isMultiplayer = true,
            price = 59.99
        ),
        Game(
            id = "sports_3",
            title = "Basketball Kings",
            categoryId = "sports",
            developer = "Hoops Entertainment",
            publisher = "Court Games",
            releaseYear = 2024,
            genre = "Basketball",
            platform = "PC, PlayStation 5, Xbox Series X, Nintendo Switch",
            rating = 4.2f,
            description = "Hit the court in the most authentic basketball experience. Create your player, join a team, and rise through the ranks to become a basketball legend.",
            imageUrl = "basketball_kings",
            features = listOf("MyCareer Mode", "Street Basketball", "Team Management", "Real Player Motion", "Community Events"),
            minPlayers = 1,
            maxPlayers = 10,
            isMultiplayer = true,
            price = 59.99
        ),

        // Indie Games
        Game(
            id = "indie_1",
            title = "Pixel Dreams",
            categoryId = "indie",
            developer = "Solo Dev Productions",
            publisher = "Indie Collective",
            releaseYear = 2023,
            genre = "Puzzle Platformer",
            platform = "PC, Nintendo Switch",
            rating = 4.9f,
            description = "A beautiful hand-crafted puzzle platformer about a little robot searching for its creator. Features mind-bending puzzles, atmospheric music, and an emotional story told without words.",
            imageUrl = "pixel_dreams",
            features = listOf("Hand-Drawn Art", "Original Soundtrack", "100+ Puzzles", "Hidden Collectibles", "Speed Run Mode"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 19.99
        ),
        Game(
            id = "indie_2",
            title = "Hollow Gardens",
            categoryId = "indie",
            developer = "Midnight Studio",
            publisher = "Indie Collective",
            releaseYear = 2022,
            genre = "Metroidvania",
            platform = "PC, PlayStation 5, Xbox Series X, Nintendo Switch",
            rating = 4.7f,
            description = "Explore a vast interconnected world filled with secrets, challenging bosses, and mysterious lore. A modern take on the metroidvania genre with tight controls and beautiful hand-drawn visuals.",
            imageUrl = "hollow_gardens",
            features = listOf("Interconnected World", "40+ Bosses", "Multiple Endings", "Challenge Modes", "Boss Rush"),
            minPlayers = 1,
            maxPlayers = 1,
            isMultiplayer = false,
            price = 24.99
        ),
        Game(
            id = "indie_3",
            title = "Cozy Farm Tales",
            categoryId = "indie",
            developer = "Heartfelt Games",
            publisher = "Wholesome Interactive",
            releaseYear = 2024,
            genre = "Farming Simulation",
            platform = "PC, Nintendo Switch",
            rating = 4.6f,
            description = "Escape to a peaceful countryside and build the farm of your dreams. Plant crops, raise animals, befriend villagers, and uncover the mysteries of the ancient forest nearby.",
            imageUrl = "cozy_farm",
            features = listOf("Farming", "Animal Care", "Village Relations", "Seasonal Events", "Co-op Mode"),
            minPlayers = 1,
            maxPlayers = 4,
            isMultiplayer = true,
            price = 29.99
        )
    )

    /**
     * Returns all available game categories.
     */
    fun getCategories(): List<Category> = categories

    /**
     * Returns a specific category by its ID.
     */
    fun getCategoryById(categoryId: String): Category? {
        return categories.find { it.id == categoryId }
    }

    /**
     * Returns all games belonging to a specific category.
     */
    fun getGamesByCategory(categoryId: String): List<Game> {
        return games.filter { it.categoryId == categoryId }
    }

    /**
     * Returns a specific game by its ID.
     */
    fun getGameById(gameId: String): Game? {
        return games.find { it.id == gameId }
    }

    /**
     * Returns all games in the collection.
     */
    fun getAllGames(): List<Game> = games
}

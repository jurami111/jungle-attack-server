Configuration Layer ---------
class GameConstants:
    +static final PORT: int
    +static final ANIMAL_SPEED: int
    +static final ENCOUNTER_RADIUS: int
    +static final FLAG_RADIUS: int
    +static final MAX_PLAYER_ENERGY: int
    +static final ATTACK_COOLDOWN_MS: int
    +static final CUP_RADIUS: int
    +static final CUP_DAMAGE_BONUS: int
    +static final BOTS_PER_ENERGY_BONUS: int
    +static final ENERGY_BONUS: int
    +static final MAX_COFFEE_CUPS: int
    +static final GAME_TICK_MILLIS: int
    +static final MAX_NETWORK_MESSAGE_CHARS: int
    +static final FORMATION_SPACING: int
    +static final MIN_ANIMAL_DISTANCE: int
    +static final PLAYER1_FLAG_COLOR: String
    +static final PLAYER2_FLAG_COLOR: String

enum AnimalSpecies:
    JAGUAR
    PUMA
    TAPIR
    PEREZOSO
    TUCAN
    QUETZAL
    COATI

Model Layer ---------
abstract class Animal:
    #id: int
    #energy: int
    #x: double
    #y: double
    -destinationX: Integer
    -destinationY: Integer
    -waypoints: Deque<int[]>
    -cooldownSeconds: double
    #Animal(int, int, int, int)
    +moveTowards(int, int): void
    +followRoute(List<int[]>): void
    +stop(): void
    +isInRange(Animal): boolean
    -nextWaypoint(): void
    -setDestination(int, int): void
    +updateMovement(double, MapCollision): void
    +updateMovement(double, MapCollision, List<Animal>): void
    -isPositionFree(double, double, List<Animal>): boolean
    +getTargetPosition(): Point
    +isMoving(): boolean
    +updateCooldown(double): void
    +isOnCooldown(): boolean
    #startCooldown(): void
    +recieveDamage(int): void
    +getEnergy(): int
    +gainEnergy(int, int): void
    +isAlive(): boolean
    +getId(): int
    +getX(): int
    +getY(): int
    +setX(int): void
    +setY(int): void

class PlayerAnimal extends Animal:
    -static final Initial_Energy: int
    -attackTarget: Animal
    +PlayerAnimal(int, int, int)
    +attack(Animal, int): boolean
    +setAttackTarget(Animal): void
    +getAttackTarget(): Animal
    +clearAttackTarget(): void

class Bot extends Animal:
    -static final INITIAL_ENERGY: int
    +Bot(int, int, int)
    +autoAttack(List<PlayerAnimal>, int): void

class Pack:
    -species: AnimalSpecies
    -damageCapacity: int
    -members: List<Animal>
    -botsKilled: int
    -animalsEliminated: int
    -cupsConsumed: int
    +Pack(AnimalSpecies, int)
    +setDamageCapacity(int): void
    +addMember(Animal): void
    +getMembers(): List<Animal>
    +getSpecies(): AnimalSpecies
    +getDamageCapacity(): int
    +registerBotKill(): int
    +registerAnimalEliminated(): void
    +registerCupConsumed(): void
    +getBotsKilled(): int
    +getAnimalsEliminated(): int
    +getCupsConsumed(): int

Animal <|-- PlayerAnimal
Animal <|-- Bot
Animal ..> GameConstants
Animal ..> MapCollision
PlayerAnimal --> Animal
Bot ..> PlayerAnimal
Pack *-- Animal
Pack --> AnimalSpecies

Game Layer --------------------
class Match:
    -matchCode: String
    -map: MapGraph
    -packs: List<Pack>
    -coffeeCups: List<CoffeeCup>
    -reportedDeaths: Set<Animal>
    -random: Random
    -player1Flag: Flag
    -player2Flag: Flag
    -winner: Pack
    -running: boolean
    -nextAnimalId: int
    -startedAtMillis: long
    -summary: MatchSummary
    +Match(String)
    +Match(String, Random)
    +start(AnimalSpecies, AnimalSpecies): void
    -createPack(AnimalSpecies, Zone, boolean): Pack
    +updateTick(double): void
    +updateMovement(double): void
    +moveCommand(Pack, List<Integer>, int, int): boolean
    -findFreeDestination(int, int, List<Point>): Point
    -buildRoute(Animal, Point, int, int): List<int[]>
    +attackCommand(Pack, List<Integer>, int): boolean
    -selectAnimals(Pack, List<Integer>): List<Animal>
    -processPlayerAttacks(): void
    -collectCoffeeCups(): void
    -checkVictory(): boolean
    -reportNewDeaths(): void
    +finishWithWinner(Pack): void
    +cancel(): void
    -playerPacks(): List<Pack>
    -isPlayerPack(Pack): boolean
    +getMatchCode(): String
    +getMap(): MapGraph
    +getPacks(): List<Pack>
    +getPlayer1Pack(): Pack
    +getPlayer2Pack(): Pack
    +getPlayer1Flag(): Flag
    +getPlayer2Flag(): Flag
    +getCoffeeCups(): List<CoffeeCup>
    +getWinner(): Pack
    +isRunning(): boolean
    +getSummary(): MatchSummary

class Zone:
    -name: String
    -x: int
    -y: int
    +Zone(String, int, int)
    +getName(): String
    +getCenterX(): int
    +getCenterY(): int

class MapFactory:
    +static createCostaRicaMap(): MapGraph
    +static buildCostaRicaCollisions(): MapCollision

class MapGraph:
    -paths: Map<Zone, Map<Zone, List<Point>>>
    -collision: MapCollision
    +addZone(Zone): void
    +addPath(Zone, Zone): void
    +addPath(Zone, Zone, List<Point>): void
    +getConnectedZones(Zone): List<Zone>
    +getPath(Zone, Zone): List<Point>
    +getPathLength(Zone, Zone): double
    -copyPoints(List<Point>): List<Point>
    +getCollision(): MapCollision
    +getZoneByCoordinates(int, int): Zone
    +isWalkable(int, int): boolean
    +getAllZones(): List<Zone>

class MapCollision:
    -walkableRects: List<Rectangle>
    -obstacleCircles: List<CircleObstacle>
    +addWalkableRect(int, int, int, int): void
    +addObstacleCircle(int, int, int): void
    +isWalkable(int, int): boolean
    +isSegmentWalkable(int, int, int, int): boolean
    +randomWalkablePoint(Random): Point

class MapCollision.CircleObstacle:
    ~centerX: int
    ~centerY: int
    ~radius: int
    +CircleObstacle(int, int, int)
    +contains(int, int): boolean

class Flag:
    -zone: Zone
    -owner: Pack
    -color: String
    -captured: boolean
    +Flag(Zone, Pack, String)
    +getZone(): Zone
    +getOwner(): Pack
    +getColor(): String
    +isCaptured(): boolean
    +capture(): void
    +isTouchedBy(Animal): boolean

class CoffeeCup:
    -x: int
    -y: int
    -consumed: boolean
    +CoffeeCup(int, int)
    +isTouchedBy(Animal): boolean
    +consumeBy(Animal): boolean
    +isConsumed(): boolean
    +getX(): int
    +getY(): int

class FogOfWar:
    +createSnapshot(Match, Pack, String): Map<String, Object>
    +canViewZone(Match, Pack, String): boolean
    -findOccupiedZones(MapGraph, Pack): Set<Zone>
    -selectZone(Set<Zone>, String): Zone
    -visibleAnimals(Match, Pack, Zone): List<Map<String, Object>>
    -visibleCups(Match, Zone): List<Map<String, Object>>
    -visibleFlags(Match, Zone): List<Map<String, Object>>
    -inZone(Match, Zone, int, int): boolean
    -animalStates(Pack, List<Animal>): List<Map<String, Object>>
    -animalState(Animal, Pack): Map<String, Object>
    -packState(Pack): Map<String, Object>
    -zoneStates(Set<Zone>): List<Map<String, Object>>
    -zoneState(Zone): Map<String, Object>

class MatchSummary:
    -winnerSpecies: AnimalSpecies
    -durationSeconds: long
    -packs: List<PackSummary>
    +MatchSummary(AnimalSpecies, long, List<PackSummary>)
    +getWinnerSpecies(): AnimalSpecies
    +getDurationSeconds(): long
    +getPacks(): List<PackSummary>

class MatchSummary.PackSummary:
    -species: AnimalSpecies
    -botsKilled: int
    -animalsEliminated: int
    -cupsConsumed: int
    +PackSummary(AnimalSpecies, int, int, int)
    +getSpecies(): AnimalSpecies
    +getBotsKilled(): int
    +getAnimalsEliminated(): int
    +getCupsConsumed(): int

Match *-- MapGraph
Match *-- Pack
Match *-- Flag
Match *-- CoffeeCup
Match --> MatchSummary
Match ..> MapFactory
Match ..> PathFinder
Match ..> GameConstants
MapFactory ..> MapGraph
MapFactory ..> MapCollision
MapGraph *-- Zone
MapGraph *-- MapCollision
MapGraph ..> MapFactory
MapCollision *-- MapCollision.CircleObstacle
Flag --> Zone
Flag --> Pack
Flag ..> GameConstants
CoffeeCup ..> PlayerAnimal
CoffeeCup ..> GameConstants
FogOfWar ..> Match
FogOfWar ..> Pack
MatchSummary *-- MatchSummary.PackSummary
MatchSummary.PackSummary --> AnimalSpecies

Algorithm Layer ---------------
class PathFinder:
    +static findShortestPath(MapGraph, Zone, Zone): List<Zone>
    -static distance(Zone, Zone): double
    -static reconstruct(Map<Zone, Zone>, Zone): List<Zone>

record PathFinder.Candidate:
    -zone: Zone
    -priority: double

PathFinder ..> MapGraph
PathFinder ..> PathFinder.Candidate
PathFinder.Candidate --> Zone

Network Layer ---------------------
class Server implements AutoCloseable:
    -listener: ServerSocket
    -matchManager: MatchManager
    -clients: Set<ClientHandler>
    +Server()
    +Server(int)
    +start(): void
    +getPort(): int
    +close(): void

class ClientHandler implements Runnable, AutoCloseable:
    -static final JSON: Gson
    -socket: Socket
    -matchManager: MatchManager
    -writer: BufferedWriter
    +ClientHandler(Socket, MatchManager)
    +run(): void
    -readMessage(BufferedReader): String
    +send(String, Object): boolean
    +sendError(String): void
    ~static string(JsonObject, String): String
    ~static integer(JsonObject, String): int
    -static integer(JsonElement, String): int
    ~static animalIds(JsonObject): List<Integer>
    +close(): void

record ClientHandler.Message:
    -type: String
    -payload: Object

class MatchManager implements AutoCloseable:
    -static final CODE_CHARS: String
    -matches: ConcurrentHashMap<String, GameSession>
    -playerSessions: ConcurrentHashMap<ClientHandler, GameSession>
    -random: SecureRandom
    -fog: FogOfWar
    -gameLoop: ScheduledExecutorService
    -closed: boolean
    +handleMessage(ClientHandler, String, JsonObject): void
    -enterLobby(ClientHandler, JsonObject, boolean): void
    -selectSpecies(GameSession, int, String): void
    -sendSelection(GameSession): void
    -update(GameSession): void
    -sendState(GameSession, String): void
    -result(Match): Map<String, Object>
    +onDisconnect(ClientHandler): void
    -finish(GameSession, String, Object): void
    +getActiveMatchCount(): int
    +close(): void

class MatchManager.GameSession:
    -code: String
    -clients: ClientHandler[]
    -species: AnimalSpecies[]
    -viewedZones: String[]
    -match: Match
    -tick: ScheduledFuture<?>
    -lastTickNanos: long
    -closed: boolean
    -GameSession(String, ClientHandler)
    -pack(int): Pack

Server *-- MatchManager
Server o-- ClientHandler
ClientHandler --> MatchManager
ClientHandler ..> ClientHandler.Message
MatchManager *-- MatchManager.GameSession
MatchManager --> FogOfWar
MatchManager.GameSession o-- ClientHandler
MatchManager.GameSession *-- Match
MatchManager.GameSession --> AnimalSpecies
MatchManager.GameSession ..> GameConstants

Application Layer ---------------------
class Main:
    +static main(String[]): void

Main ..> Server

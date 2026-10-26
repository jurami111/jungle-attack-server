# Contrato TCP: cliente y servidor

Esta es la forma actual de los mensajes. Los ejemplos completos estan en
[cliente-a-servidor.jsonl](cliente-a-servidor.jsonl) y
[servidor-a-cliente.jsonl](servidor-a-cliente.jsonl).
Son ejemplos de formato, no una partida grabada: hay que sustituir el codigo,
IDs y posiciones por los recibidos en la conexion real.

## Conexion

- Iniciar el servidor con `make start`. TCP, puerto `12345`, UTF-8.
- En el cliente: `new Socket(host, 12345)`. `localhost` solo si ambos programas
  corren en la misma computadora; de otro modo, usar la IP del servidor.
- No es HTTP: no hay URL, endpoint web ni archivo al que subir un JSON.
- Cada conexion mantiene su socket abierto durante todo el lobby y la partida.
- Cada mensaje es un JSON completo en UNA linea, con salto de linea y `flush()`.
- Envolver siempre en `{"type":"...","payload":{...}}`. Usar Gson/Jackson.
- El limite de entrada es 8192 caracteres por mensaje; un exceso cierra la conexion.
- La lectura de mensajes debe estar fuera del hilo de Swing. La interfaz se
  actualiza con `SwingUtilities.invokeLater(...)`, no desde el lector TCP.
- No enviar el pack propietario, energia, dano, bandera capturada o ganador:
  el servidor calcula estos valores y determina el propietario por conexion.

**Cambio deliberado:** ahora solo se aceptan los nombres del documento del
profesor: `SELECT_ANIMAL`, `MOVE_ANIMALS`, `STATE_UPDATE`.
Los nombres anteriores `SELECT_SPECIES`, `MOVE`, `GAME_STATE` se retiraron.

## Cliente -> servidor: cada payload y su receptor

TODOS llegan a
[ClientHandler.run()](../src/main/java/cr/ac/tec/jungleattack/server/network/ClientHandler.java),
que lee/valida JSON y llama a
[MatchManager.handleMessage()](../src/main/java/cr/ac/tec/jungleattack/server/network/MatchManager.java).
No se mandan directamente a un archivo Java ni a `Match`.

### CREATE_MATCH

```json
{"type":"CREATE_MATCH","payload":{"nickname":"Ana"}}
```

- Generar en el controlador del lobby del cliente al pulsar Crear.
- Receptor logico: `MatchManager.enterLobby(..., true)`.
- Respuesta: `MATCH_CREATED` con codigo de cuatro caracteres.
- Solo una partida por conexion. El apodo debe tener entre 1 y 24 caracteres.

### JOIN_MATCH

```json
{"type":"JOIN_MATCH","payload":{"nickname":"Luis","code":"A7K2"}}
```

- Generar en el controlador del lobby del cliente al pulsar Unirse.
- Receptor logico: `MatchManager.enterLobby(..., false)`.
- Respuesta: `MATCH_JOINED`, o `ERROR` si el codigo no existe o esta llena.
- El codigo no distingue mayusculas; no usar literalmente A7K2 si se recibio otro.

### SELECT_ANIMAL

```json
{"type":"SELECT_ANIMAL","payload":{"species":"MONO"}}
```

- Generar en el selector de especie del cliente despues de entrar los dos jugadores.
- Receptor logico: `MatchManager.selectSpecies()`.
- Valores: `MONO`, `MARIPOSA`, `RANA`, `PEREZOSO`, `SERPIENTE`, `TORTUGA`, `YIGUIRRO`.
- Una eleccion confirmada por jugador. Las especies no pueden repetirse.
- Respuesta: `ANIMAL_SELECTION`; al confirmar ambos, `GAME_STARTED`.
- No enviar un comando START: la partida empieza desde el servidor.

### MOVE_ANIMALS

```json
{"type":"MOVE_ANIMALS","payload":{"animalIds":[1,2],"targetX":910,"targetY":316}}
```

- Generar en el controlador del mapa: seleccion actual + destino del clic.
- Receptor logico: `MatchManager.handleMessage()` -> `Match.moveCommand()`.
- `animalIds` debe ser una lista no vacia de IDs propios vivos, obtenidos de
  `ownAnimals`; no son IDs de packs ni posiciones en una lista.
- `targetX` y `targetY` son enteros en coordenadas GLOBALES del mapa del servidor,
  no coordenadas del panel, de una zona, de la pantalla o afectadas por zoom.
- La orden avanza en ticks; no reenviarla en cada frame.
- El clic es el centro de la formacion: cada animal conserva un slot estable
  separado 32 px en X y Y, incluso con ordenes individuales. Si esta ocupado por
  un companero, se busca otro destino cercano, libre y caminable.
  Esto evita apilar diez ordenes individuales en el mismo punto.
- No hay ACK de movimiento: su resultado se observa en `STATE_UPDATE`.
- Los destinos y recorridos bloqueados se rechazan antes de sustituir una ruta
  activa. El servidor valida todos los tramos, incluido el acceso desde la
  posicion actual y la salida hasta el slot. Las conexiones siguen puntos
  intermedios definidos en MapFactory; A* usa su longitud real.
- La seleccion completa se acepta o rechaza como una sola orden. Si un
  companero bloquea el siguiente paso, el animal espera sin borrar la ruta.
- Las pruebas cubren los 132 pares de centros de zona con los diez animales,
  no cualquier orden cruzada a destinos arbitrarios. Un bloqueo mutuo entre
  companeros puede necesitar una nueva orden: no hay un sistema de trafico general.

### ATTACK

```json
{"type":"ATTACK","payload":{"animalIds":[1,2],"targetId":21}}
```

- Generar al hacer clic en un animal enemigo o bot visible con animales propios seleccionados.
- Receptor logico: `MatchManager.handleMessage()` -> `Match.attackCommand()`.
- `targetId` es UN animal, no la especie ni el pack.
- Se acepta si al menos un seleccionado esta vivo y a distancia <= 30.
- El ataque se repite una vez por segundo hasta morir el objetivo o salir de rango.
- No hacer un timer de dano en el cliente ni enviar esta orden cada segundo.
- Resultado en `STATE_UPDATE`; rechazo en `ERROR`. No hay comando especial STOP.

### VIEW_ZONE

```json
{"type":"VIEW_ZONE","payload":{"zone":"Monteverde"}}
```

- Generar al cambiar de zona en la interfaz.
- Receptor logico: `MatchManager.handleMessage()` -> `FogOfWar.canViewZone()`.
- Elegir el nombre de una zona de `visibleZones`.
- Se permite solo si un animal propio vivo ocupa actualmente esa zona.
- Devuelve `STATE_UPDATE` inmediatamente; los ticks usan esa seleccion.
- Si la zona deja de estar ocupada, el siguiente estado elige otra zona ocupada,
  o devuelve `selectedZone: null` si no queda ninguna. No conserva exploracion historica.

## Servidor -> cliente: cada payload y su generador

TODOS salen por
[ClientHandler.send()](../src/main/java/cr/ac/tec/jungleattack/server/network/ClientHandler.java).
Este metodo agrega la envoltura, serializa con Gson y escribe la linea TCP.
El cliente recibe TODOS por el mismo `BufferedReader.readLine()` de su hilo de red
y despacha segun `type`; no abre un socket por mensaje.

| Tipo | Payload completo en ejemplo | Donde se construye | Receptor recomendado en el cliente |
|---|---|---|---|
| `MATCH_CREATED` | `{"code":"A7K2"}` | `MatchManager.enterLobby()` | Modelo/controlador del lobby: mostrar codigo y esperar |
| `MATCH_JOINED` | `{"code":"A7K2"}` | `MatchManager.enterLobby()` | Lobby: confirmar entrada |
| `PLAYER_JOINED` | `{"nickname":"Luis"}` | `MatchManager.enterLobby()` | Lobby del creador: mostrar rival |
| `ANIMAL_SELECTION` | Ver debajo | `MatchManager.sendSelection()` | Selector: actualizar disponibilidad |
| `GAME_STARTED` | Snapshot completo | `MatchManager.sendState()` + `FogOfWar.createSnapshot()` | Modelo de partida: inicializar y abrir vista |
| `STATE_UPDATE` | Snapshot completo | `MatchManager.update()` o `VIEW_ZONE`, usando `FogOfWar.createSnapshot()` | Modelo: sustituir estado, notificar vista |
| `GAME_OVER` | Ganador, duracion y siete packs | `MatchManager.result()` desde `MatchSummary` | Detener partida, mostrar resumen |
| `MATCH_CANCELLED` | `{"reason":"The other player disconnected"}` | `MatchManager.onDisconnect()` | Volver al lobby, sin ganador |
| `ERROR` | `{"message":"The other player already chose that species"}` | `ClientHandler.sendError()` | Mostrar mensaje; normalmente conservar conexion |

### ANIMAL_SELECTION

```json
{"type":"ANIMAL_SELECTION","payload":{"availableSpecies":["MARIPOSA","RANA","PEREZOSO","SERPIENTE","TORTUGA","YIGUIRRO"],"yourSpecies":"MONO","opponentSpecies":null}}
```

Se envia a los dos despues de entrar el segundo y despues de cada eleccion.
`yourSpecies` y `opponentSpecies` pueden ser `null` antes de elegir.
El apodo del creador no se incluye en la respuesta del segundo; no asumir ese campo.

### GAME_STARTED / STATE_UPDATE

El archivo [servidor-a-cliente.jsonl](servidor-a-cliente.jsonl) contiene ambos
JSON completos con diez animales propios, sin puntos suspensivos.

Campos exactos del payload:

| Campo | Forma / significado |
|---|---|
| `matchCode` | String de cuatro caracteres |
| `running` | Boolean; false en el ultimo snapshot de una partida terminada |
| `ownPack` | `{species,damageCapacity,botsKilled,animalsEliminated,cupsConsumed}` |
| `ownFlagColor` | Color hexadecimal RGB de tu bandera, aunque no este en la zona vista |
| `ownAnimals` | Todos tus animales, incluso muertos: `{id,species,x,y,energy,alive}` |
| `visibleZones` | Zonas actualmente ocupadas por animales propios vivos: `{name,centerX,centerY}` |
| `selectedZone` | Una zona de esa lista, o `null` |
| `visibleAnimals` | Enemigos vivos de esa zona: `{id,species,x,y,energy,alive,bot}` |
| `visibleCoffeeCups` | Tazas sin consumir en esa zona: `{x,y}` |
| `visibleFlags` | Banderas de esa zona: `{owner,x,y,color,captured}` |

Los animales propios y visibles pueden incluir `attack`:
`{"sequence":1,"targetX":440,"targetY":400}`. Solo se envia durante los 300 ms
posteriores a un golpe que aplico dano. La secuencia aumenta por animal, no
por snapshot; el cliente inicia una animacion al recibir una secuencia nueva,
sin calcular dano ni reiniciarla en cada tick. La ausencia de `attack` es valida
para clientes/servidores anteriores. Los bots patrullan automaticamente entre
zonas conectadas y se detienen cuando un jugador entra en su radio de ataque.

Rojo `#E53935` = creador de partida; azul `#1E88E5` = segundo jugador.
La propiedad se identifica por `owner` (especie), NO por interpretar el color.

Cada snapshot reemplaza las listas visibles anteriores. No acumular enemigos o
tazas ocultos en el cliente. Una desaparicion no prueba una muerte: puede ser
un cambio de zona. `ownAnimals` contiene informacion propia global para seleccionar
exploradores, pero la vista solo dibuja la zona seleccionada.

El servidor NO envia el grafo, todas las zonas ni un SVG. Los recursos graficos
por zona deben existir en el cliente. La asignacion de zonas actual usa el centro
mas cercano a cada coordenada, con desempate por orden de registro; no hay radio
de vision ni huecos entre zonas. Esta particion no modifica los rectangulos de colision.

### GAME_OVER

```json
{"type":"GAME_OVER","payload":{"winner":"MONO","durationSeconds":180,"packs":[{"species":"MONO","botsKilled":2,"animalsEliminated":1,"cupsConsumed":1},{"species":"MARIPOSA","botsKilled":0,"animalsEliminated":0,"cupsConsumed":0},{"species":"RANA","botsKilled":0,"animalsEliminated":2,"cupsConsumed":0},{"species":"PEREZOSO","botsKilled":0,"animalsEliminated":0,"cupsConsumed":0},{"species":"SERPIENTE","botsKilled":0,"animalsEliminated":0,"cupsConsumed":0},{"species":"TORTUGA","botsKilled":0,"animalsEliminated":0,"cupsConsumed":0},{"species":"YIGUIRRO","botsKilled":0,"animalsEliminated":0,"cupsConsumed":0}]}}
```

No hay un objeto anidado `summary`. `packs` tiene SIETE entradas, en orden:
dos jugadores, luego cinco bots. `animalsEliminated` son las bajas sufridas por
ese pack, no los enemigos que elimino. `botsKilled` son bots abatidos por ese
jugador. Los bots no consumen cafe ni ganan bonos, por lo que sus otros contadores
son cero. La duracion usa reloj real, no ticks.

Se conserva la victoria por bandera y la victoria por eliminacion total.
No se entrega una victoria por desconexion: se cancela la partida.
Tras `GAME_OVER`/`MATCH_CANCELLED`, una conexion que siga abierta puede crear
otra partida o unirse con otro codigo.

## Orden de mensajes y responsabilidades del cliente

1. A conecta y envia `CREATE_MATCH`; recibe `MATCH_CREATED`.
2. B conecta y envia `JOIN_MATCH`; recibe `MATCH_JOINED`.
3. A recibe `PLAYER_JOINED`; ambos reciben `ANIMAL_SELECTION`.
4. Ambos envian `SELECT_ANIMAL` con especies distintas.
5. Ambos reciben `GAME_STARTED`, luego `STATE_UPDATE` aproximadamente cada 33 ms.
6. El cliente envia intenciones; el servidor calcula movimiento, dano, cafes y resultado.
7. En final normal: ultimo `STATE_UPDATE`, luego `GAME_OVER`.
8. Si se pierde un jugador: `MATCH_CANCELLED` al restante. Si se cierra el
   servidor o se corta TCP: el cliente detecta EOF/error y muestra desconexion.

Los dos hilos de conexion pueden enviar ordenes simultaneas. En particular, no
suponer que la especie de A se acepta antes que la de B: usar las respuestas.
Para dibujar suavemente se pueden interpolar snapshots, sin alterar el estado
autoritativo ni calcular victorias localmente.

**Estado de integracion:** probado con clientes TCP de prueba. El repositorio
cliente no fue proporcionado: no se conocen sus nombres reales de archivos y
no se certifica que ya lea este contrato. No debe importar clases de `server.game`.

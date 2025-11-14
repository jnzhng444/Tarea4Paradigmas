# Estructura de Paquetes - Servidor Donkey Kong Jr

## Organización del Código Java

El código del servidor está organizado en los siguientes paquetes (carpetas):

###  com.dkj.main
**Propósito**: Punto de entrada de la aplicación
- `ServerMain.java` - Clase principal con método main()

###  com.dkj.server
**Propósito**: Gestión de conexiones y sesiones de red
- `LineServer.java` - Servidor TCP que acepta conexiones
- `ClientContext.java` - Contexto de cada cliente conectado
- `SessionRegistry.java` - Registro de sesiones activas
- `MatchRegistry.java` - Registro de partidas y salas de juego

###  com.dkj.game
**Propósito**: Lógica del juego y mecánicas
- `Game.java` - Lógica principal del juego
- `GameRoom.java` - Sala de juego para un jugador con espectadores
- `GameLoop.java` - Bucle de actualización del juego
- `GameRules.java` - Constantes de física y reglas del juego
- `Level.java` - Definición del nivel (lianas, plataformas, posiciones)
- `PhysicsEngine.java` - Motor de física (colisiones, movimiento, gravedad)

###  com.dkj.entities
**Propósito**: Entidades del juego
- `Entity.java` - Interfaz sealed para todas las entidades
- `CrocodileRed.java` - Cocodrilo rojo (vertical en lianas)
- `CrocodileBlue.java` - Cocodrilo azul (horizontal en plataformas/lianas)
- `Fruit.java` - Fruta coleccionable
- `Player.java` - Representación del jugador
- `DefaultEntityFactory.java` - Factory Method para crear entidades

###  com.dkj.model
**Propósito**: Clases de datos y records
- `Direction.java` - Enum de direcciones (LEFT, RIGHT, UP, DOWN, NONE)
- `Height.java` - Record para altura lógica (0-12)
- `LianaId.java` - Record para identificar lianas
- `PlayerId.java` - Record para identificar jugadores (UUID)
- `Points.java` - Record para puntos
- `Position.java` - Record para posición (liana + altura)
- `Speed.java` - Record para velocidad
- `Role.java` - Enum de roles (PLAYER, SPECTATOR)
- `Liana.java` - Record para datos de una liana (x, topY, bottomY)
- `Platform.java` - Record para plataforma (x, y, width, height)
- `PlayerPhysics.java` - Estado físico del jugador (posición, velocidad, vidas, score)

###  com.dkj.events
**Propósito**: Sistema de eventos (Observer Pattern)
- `GameEvent.java` - Interfaz sealed para eventos del juego
- `StateEvent.java` - Evento de actualización de estado
- `ScoreEvent.java` - Evento de cambio de puntuación
- `DeathEvent.java` - Evento de muerte del jugador
- `LevelEvent.java` - Evento de cambio de nivel
- `GameEventBus.java` - Bus de eventos para notificaciones
- `GameEventListener.java` - Interfaz para escuchar eventos

###  com.dkj.commands
**Propósito**: Procesamiento de comandos del protocolo
- `CommandDispatcher.java` - Interfaz para despachadores de comandos
- `CommandDispatcherWithGame.java` - Despachador con lógica de juego
- `MoveCommand.java` - Record para comandos de movimiento

###  com.dkj.admin
**Propósito**: Consola de administración
- `AdminConsole.java` - Consola de administración en terminal
- `AdminWindow.java` - Ventana gráfica de administración (Swing)

## Patrones de Diseño Implementados

1. **Observer Pattern**
   - `GameEventBus` + `GameEventListener` + eventos (`StateEvent`, `ScoreEvent`, `DeathEvent`, `LevelEvent`)
   - Los observadores se suscriben a eventos del juego

2. **Factory Method**
   - `DefaultEntityFactory` crea instancias de `CrocodileRed`, `CrocodileBlue`, `Fruit`
   - Permite extensibilidad para crear nuevas variantes de entidades

## Compilación

```bash
cd ServidorJava
javac -d . com/dkj/**/*.java
```

O de forma recursiva:
```bash
$files = Get-ChildItem -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
javac -d . @files
```

## Ejecución

```bash
cd ServidorJava
java com.dkj.main.ServerMain
```

El servidor escuchará en el puerto 5555 por defecto.

## Beneficios de la Estructura de Paquetes

-  **Organización clara**: Cada paquete tiene una responsabilidad específica
-  **Mantenibilidad**: Fácil localizar y modificar código relacionado
-  **Escalabilidad**: Agregar nuevas funcionalidades sin afectar otros módulos
-  **Encapsulación**: Controlar visibilidad (public/package-private) entre paquetes
-  **Profesionalismo**: Estructura estándar de proyectos Java empresariales

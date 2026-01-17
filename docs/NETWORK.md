# Network

> Ultimo aggiornamento: 2026-01-15

DevMod usa payload NeoForge registrati in `com.devmod.network.NetworkHandler` con mapping in `com.devmod.network.ChannelId`.

## Struttura

- Registry centralizzato con id numerici e direction (client->server / server->client).
- Validazione payload e limiti dimensione con `PayloadValidation`.
- Handler separati per dominio (endurance, party, config, mailbox, ecc.).

## Domini principali (per range ChannelId)

- 1-4: mob/item editor
- 5-25: endurance quest
- 26-35: party
- 36-55: config/editor stats + mechanics
- 56-65: shield/impact
- 66-75: abilities
- 76-85: arena
- 86-89: challenges
- 90-99: debug
- 100-115: mailbox/news/task/ticket
- 120-129: notification center
- 130-139: compat/nutrition/mob pool
- 140-149: nexus
- 150-159: portal
- 160-169: hologram
- 170-179: clone
- 180-189: npc
- 190-199: area builder
- 200-209: zone marker
- 210-218: transport

## Nota inventario

Il dettaglio completo dei payload e dei canali e in `docs/IMPLEMENTATION_STATE.md`.

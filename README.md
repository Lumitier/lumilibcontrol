# LumiLibControl

Client-side Fabric mod that adds an admin menu for [LumiLibControlPlugin](https://github.com/Lumitier/lumilibcontrolplugin) to the LumiLib hub.

Open the hub, select **LumiLibControl** and switch Lumitier's mods on or off for everyone on the server – no commands needed.

## What you see

- **As an admin** (operator or `lumimod.admin`): one switch per mod. Changes apply instantly to every player on the server.
- **Without permission:** the current state of each mod, and the note *"You don't have permission to change the mods on this server."*
- **On a server without the plugin, or in singleplayer:** *"This server doesn't use LumiLibControl."*

The menu updates on its own when another admin changes something or when your permissions change.

## Who needs this mod?

Only admins who want to manage the server from inside the game. Regular players don't need it – the server rules reach them through LumiLib alone. Admins can also use `/lumimod` or the config file instead.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.20.1+
- Fabric API
- [LumiLib](https://modrinth.com/mod/lumilib) 1.3.1+
- [LumiLibControlPlugin](https://github.com/Lumitier/lumilibcontrolplugin) on the server

## License

Apache-2.0

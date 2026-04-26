# TangledMaze

A spigot plugin for easily creating and customizing mazes in Minecraft. With a WorldEdit like wand you can visually select areas to build a maze in. This way you have full control over the shape (and dimensions) of your mazes!

For downloads & more information:
<https://www.spigotmc.org/resources/tangled-maze.59284/>


## Features
- Building mazes from rectangles, circles and triangles
- Classic grid and polar maze layouts
- Maze generation that adapts to height differences in the terrain
- Highly customizable maze settings for sizes of paths, walls and composition of building blocks
- Ability to set multiple entrances and exits for a maze
- Possibility to add a roof and change the floor of the maze
- Maze solving feature to see the path connecting all exits in a maze

## Development

To be able to compile the plugin with the [LootChest](https://www.spigotmc.org/resources/lootchest.61564/) dependency, you need to install the LootChest jar file in your local maven repository.
You can do this by running the following commands in the directory where the LootChest.jar file is located:
```shell
mvn install:install-file -Dfile=<path/to/lootchest.jar> -DgroupId=fr.black_eyes.lootchest -DartifactId=lootchest -Dversion=2.5.0 -Dpackaging=jar
mvn install:install-file -Dfile=<path/to/lootchest.jar> -DgroupId=fr.black_eyes.lootchest -DartifactId=fall_effect -Dversion=2.5.0 -Dpackaging=jar
```

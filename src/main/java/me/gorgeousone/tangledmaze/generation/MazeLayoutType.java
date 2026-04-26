package me.gorgeousone.tangledmaze.generation;

import java.util.Arrays;

/**
 * Layouts used to turn a selected maze area into generated paths.
 */
public enum MazeLayoutType {
	GRID,
	POLAR;

	public String commandName() {
		return toString().toLowerCase();
	}

	public String textName() {
		return commandName();
	}

	public static String[] commandNames() {
		return Arrays.stream(values()).map(MazeLayoutType::commandName).toArray(String[]::new);
	}

	public static MazeLayoutType match(String playerInput) {
		for (MazeLayoutType layout : values()) {
			if (layout.commandName().equalsIgnoreCase(playerInput)) {
				return layout;
			}
		}
		return null;
	}
}

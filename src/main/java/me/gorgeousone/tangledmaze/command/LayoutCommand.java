package me.gorgeousone.tangledmaze.command;

import me.gorgeousone.tangledmaze.SessionHandler;
import me.gorgeousone.tangledmaze.cmdframework.argument.ArgType;
import me.gorgeousone.tangledmaze.cmdframework.argument.ArgValue;
import me.gorgeousone.tangledmaze.cmdframework.argument.Argument;
import me.gorgeousone.tangledmaze.cmdframework.command.ArgCommand;
import me.gorgeousone.tangledmaze.data.Message;
import me.gorgeousone.tangledmaze.generation.MazeLayoutType;
import me.gorgeousone.tangledmaze.maze.MazeSettings;
import me.gorgeousone.tangledmaze.util.text.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The command to choose how maze paths are laid out inside the selected area.
 */
public class LayoutCommand extends ArgCommand {

	private final SessionHandler sessionHandler;

	public LayoutCommand(SessionHandler sessionHandler) {
		super("layout");
		addAlias("l");
		addArg(new Argument("layout", ArgType.STRING, MazeLayoutType.commandNames()).setDefault(""));
		this.sessionHandler = sessionHandler;
	}

	@Override
	protected void executeArgs(CommandSender sender, List<ArgValue> argValues, Set<String> usedFlags) {
		UUID playerId = getSenderId(sender);
		String layoutName = argValues.get(0).get();
		MazeSettings settings = sessionHandler.getSettings(playerId);

		if (layoutName.isEmpty()) {
			Message.INFO_LAYOUT_INFO.sendTo(
					sender,
					new Placeholder("layout", settings.getLayoutType().textName()));
			return;
		}
		MazeLayoutType layoutType = MazeLayoutType.match(layoutName);

		if (layoutType == null) {
			Message.ERROR_INVALID_LAYOUT.sendTo(sender, new Placeholder("layout", layoutName));
			return;
		}
		settings.setLayoutType(layoutType);
		Message.INFO_LAYOUT_CHANGE.sendTo(
				sender,
				new Placeholder("layout", layoutType.textName()));
	}
}

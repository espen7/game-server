package game.engine.core.hotfix.demo;

public class DefaultLogic implements LogicStrategy {
    @Override
    public String execute(String input) {
        return "Default Logic: " + input;
    }
}

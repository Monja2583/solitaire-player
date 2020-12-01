package com.secondthorn.solitaireplayer.players.pyramid;

import com.secondthorn.solitaireplayer.players.PlayException;
import com.secondthorn.solitaireplayer.players.SolitairePlayer;
import com.secondthorn.solitaireplayer.solvers.pyramid.Action;
import com.secondthorn.solitaireplayer.solvers.pyramid.BoardChallengeSolver;
import com.secondthorn.solitaireplayer.solvers.pyramid.CardChallengeSolver;
import com.secondthorn.solitaireplayer.solvers.pyramid.Deck;
import com.secondthorn.solitaireplayer.solvers.pyramid.PyramidSolver;
import com.secondthorn.solitaireplayer.solvers.pyramid.ScoreChallengeSolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.sikuli.script.Sikulix.popAsk;
import static org.sikuli.script.Sikulix.popSelect;

/**
 * Guides the user through a game of Pyramid Solitaire. It interacts with the Microsoft Solitaire Collection window as
 * well as the user, from the start of the game until a solution is played or reported.
 */
public class PyramidPlayer extends SolitairePlayer {
    /**
     * A list of steps to lose quickly for Board Challenges that can't be cleared.
     */
    private static List<Action> loseQuickly;

    static {
        loseQuickly = new ArrayList<>();
        for (int cycle = 1; cycle <= 3; cycle++) {
            for (int deckCard = 0; deckCard < 24; deckCard++) {
                loseQuickly.add(Action.newDrawAction());
            }
            if (cycle < 3) {
                loseQuickly.add(Action.newRecycleAction());
            }
        }
    }

    /**
     * Figures out the steps to take to reach the solitaire goal.
     */
    private PyramidSolver solver;

    /**
     * Creates a new PyramidPlayer instance based on command line args for Board, Score, or Card Challenges.
     * The args can be:
     * <ul>
     * <li>Board</li>
     * <li>Score</li>
     * <li>Score [goal score] [current score]</li>
     * <li>Card [goal number of cards to clear] [goal card rank] [current number of cards cleared]</li>
     * </ul>
     *
     * @param args command line args describing the goal for the solution
     * @throws IllegalArgumentException if the args don't properly describe a challenge
     */
    public PyramidPlayer(String[] args) {
        String goalType = args.length > 0 ? args[0] : "board";
        switch (goalType.toLowerCase()) {
            case "board":
                solver = new BoardChallengeSolver();
                System.out.println("Starting a Pyramid Solitaire Board Challenge...");
                break;
            case "score":
                int goalScore = (args.length > 1) ? parseInt(args[1]) : ScoreChallengeSolver.MAX_POSSIBLE_SCORE;
                int currentScore = (args.length > 2) ? parseInt(args[2]) : 0;
                solver = new ScoreChallengeSolver(goalScore, currentScore);
                System.out.print("Starting a Pyramid Solitaire Score Challenge: ");
                if (args.length == 1) {
                    System.out.println("find the maximum possible score...");
                } else {
                    System.out.println("go from " + currentScore + " to " + goalScore + " points...");
                }
                break;
            case "card":
                if (args.length != 4) {
                    throw new IllegalArgumentException("Wrong number of args for Pyramid Solitaire Card Challenge");
                }
                int goalNumCardsToClear = parseInt(args[1]);
                char cardRankToClear = parseCardRank(args[2]);
                int currentNumCardsCleared = parseInt(args[3]);
                solver = new CardChallengeSolver(goalNumCardsToClear, cardRankToClear, currentNumCardsCleared);
                System.out.print("Starting a Pyramid Solitaire Card Challenge: ");
                System.out.print("clear " + goalNumCardsToClear + " cards of rank " + cardRankToClear);
                System.out.println(", with " + currentNumCardsCleared + " cleared so far");
                break;
            default:
                throw new IllegalArgumentException("Unknown goal for Pyramid Solitaire: " + goalType);
        }
    }

    /**
     * Plays the currently displayed Microsoft Solitaire Collection Pyramid Solitaire game, using SikuliX to automate
     * the actions and scan the cards on the screen.
     *
     * @throws InterruptedException if the thread is interrupted
     * @throws PlayException        if there's a problem while playing the game
     */
    @Override
    public void autoplay() throws InterruptedException, PlayException {
        PyramidWindow window = new PyramidWindow();
        window.undoBoard();
        window.moveMouse(1, 1);
        List<String> cards = scanCardsOnScreen(window);
        Deck deck = new Deck(verifyCards(cards));
        Map<String, List<Action>> solutions = solver.solve(deck);
        printSolutions(solutions);
        List<Action> solutionToPlay = chooseSolution(solutions);
        window.undoBoard();
        window.moveMouse(1, 1);
        playSolution(solutionToPlay, window);
    }

    /**
     * Prints out the solution(s) but doesn't do any SikuliX-based automation to play the game.
     *
     * @throws PlayException if the user cancels while the program is verifying the deck of cards
     */
    @Override
    public void preview(String filename) throws PlayException {
        List<String> cards = readCardsFromFile(filename);
        Deck deck = new Deck(verifyCards(cards));
        Map<String, List<Action>> solutions = solver.solve(deck);
        printSolutions(solutions);
    }

    /**
     * Returns true if the list of cards works for playing Pyramid Solitaire.  It must be a standard deck of 52 cards
     * without any unknown cards.
     *
     * @param cards           a list of cards to check
     * @param missing         the cards in the list that are missing from the standard 52 card deck
     * @param duplicates      the cards in the list that are duplicates, not including unknown cards
     * @param malformed       the cards in the list that are not cards and not unknown cards (garbage input)
     * @param numUnknownCards the number of unknown cards, represented by "??""
     * @return true if the list of cards is suitable for playing Pyramid Solitaire.
     */
    @Override
    protected boolean isValidCards(List<String> cards,
                                   List<String> missing,
                                   List<String> duplicates,
                                   List<String> malformed,
                                   long numUnknownCards) {
        return ((cards.size() == 52) &&
                (missing.size() == 0) &&
                (duplicates.size() == 0) &&
                (malformed.size() == 0) &&
                (numUnknownCards == 0));
    }

    /**
     * Returns a String containing the cards in a Pyramid Solitaire pattern. It contains the 28 card pyramid, and a
     * list of the rest of the cards starting from the top of the stock pile to the bottom.
     * The argument must be a list of 52 cards (some may be unknown, represented by ??)
     *
     * @param cards a list of cards
     * @return a String representation of the cards laid out in a Pyramid Solitaire game setup
     */
    @Override
    protected String cardsToString(List<String> cards) {
        return String.format(
                "            %s\n" +
                        "          %s  %s\n" +
                        "        %s  %s  %s\n" +
                        "      %s  %s  %s  %s\n" +
                        "    %s  %s  %s  %s  %s\n" +
                        "  %s  %s  %s  %s  %s  %s\n" +
                        "%s  %s  %s  %s  %s  %s  %s\n" +
                        "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s",
                cards.toArray());
    }

    /**
     * Prints all solutions to the given deck, according to the solver. There may be more than one solution when playing
     * a Card Challenge, so the input is a mapping from solution description string to a list of Actions.
     *
     * @param solutions the results from the Pyramid Solitaire solver
     */
    private void printSolutions(Map<String, List<Action>> solutions) {
        for (Map.Entry<String, List<Action>> entry : solutions.entrySet()) {
            System.out.println("Solution: " + entry.getKey());
            for (Action action : entry.getValue()) {
                System.out.println("    " + action);
            }
        }
    }

    /**
     * Looks through all the cards in the game and returns a list of the cards seen.
     * The cards may be wrong and must be verified and corrected by the user.
     *
     * @param window the PyramidWindow to help read cards on the screen
     * @return a list of all the cards found on the screen
     * @throws InterruptedException if the thread is interrupted
     * @throws PlayException        if there's a problem looking for cards in the Microsoft Solitaire Collection window
     */
    private List<String> scanCardsOnScreen(PyramidWindow window) throws InterruptedException, PlayException {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            cards.add(window.cardAtPyramid(i));
        }
        for (int i = 0; i < 24; i++) {
            String card = window.cardAtDeck();
            cards.add(card);
            window.draw();
        }
        window.undoBoard();
        return cards;
    }

    /**
     * Given zero or more solutions from the solver, have the user select one, or cancel and exit.
     *
     * @param solutions a list of solutions generated by the solver
     * @return one of the solutions, a list of Actions
     * @throws PlayException if the user cancels and wants to exit without automatically playing the game
     */
    private List<Action> chooseSolution(Map<String, List<Action>> solutions) throws PlayException {
        List<Action> solution;
        String solutionDescription;
        switch (solutions.size()) {
            case 0:
                solutionDescription = "No solution found, so lose quickly to get to the next deal.";
                solution = loseQuickly;
                break;
            case 1:
                solutionDescription = solutions.keySet().iterator().next();
                solution = solutions.get(solutionDescription);
                break;
            default:
                String[] keys = solutions.keySet().toArray(new String[0]);
                Arrays.sort(keys);
                String message = "Select a solution, Cancel to exit without automatically playing.";
                String title = "Multiple Solutions Found";
                if (showPrompts) {
                    solutionDescription = popSelect(message, title, keys);
                } else {
                    solutionDescription = keys[0];
                }
                solution = solutions.get(solutionDescription);
                break;
        }
        StringBuilder confirmMessage = new StringBuilder();
        confirmMessage.append("Press Yes to play solution, No to quit.\n");
        confirmMessage.append("Solution: ");
        confirmMessage.append(solutionDescription);
        confirmMessage.append("\n");

        if (!showPrompts) {
            return solution;
        } else if ((solution != null) && popAsk(confirmMessage.toString(), "Play the solution?")) {
            return solution;
        } else {
            throw new PlayException("User cancelled selecting and playing a solution.");
        }
    }

    /**
     * Performs a list of actions generated by the solver on the game window.
     *
     * @param solution a list of Actions to perform
     * @param window   the PyramidWindow to perform the actions on
     * @throws InterruptedException if the thread is interrupted
     * @throws PlayException        if there's a problem clicking on cards
     */
    private void playSolution(List<Action> solution, PyramidWindow window) throws InterruptedException, PlayException {
        for (Action action : solution) {
            switch (action.getCommand()) {
                case DRAW:
                    window.draw();
                    break;
                case RECYCLE:
                    window.recycle();
                    break;
                case REMOVE:
                    for (int i = 0; i < action.getPositions().size(); i++) {
                        String position = action.getPositions().get(i);
                        switch (position) {
                            case "Stock":
                                window.clickStockCard();
                                break;
                            case "Waste":
                                window.clickWasteCard();
                                break;
                            default:
                                window.clickPyramidCardIndex(Integer.parseInt(position));
                        }
                    }
                    break;
            }
        }
    }
}

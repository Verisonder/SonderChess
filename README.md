# SonderChess

Free game review for your Chess.com games, on your phone.

Finish a game on Chess.com, tap the share button, pick **SonderChess**. The app fetches the game,
runs Stockfish on the phone and gives you a full review: accuracy, a label for every move, the
evaluation graph and the engine's better moves. No account, no subscription, no server.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## What you get

- **Share to review.** Works with Chess.com's share button for live and bot games. You can also
  paste a game link or any PGN.
- **Summary.** Accuracy for both players, a count of every move label, an estimated game rating,
  the opening's name and the evaluation graph.
- **Move by move.** Each move gets a label: Brilliant, Great, Best, Excellent, Good, Book,
  Inaccuracy, Mistake, Miss or Blunder. The coach says what the move did, an arrow shows the
  better move, and **Best** plays it on the board.
- **On your phone.** Stockfish 17.1 runs on the device. Choose Fast, Normal or Deep analysis.
- **History.** Every review is kept so you can reopen it.

## Requirements

Android 8.0 or newer on a 64-bit ARM phone.

## Building

```
tools/fetch_stockfish.sh      # downloads the official Stockfish build for Android
gradle assembleDebug
```

## Credits

- [Stockfish](https://stockfishchess.org) (GPL-3.0), the chess engine.
- Chess pieces based on the [RhosGFX](https://github.com/lichess-org/lila/tree/master/public/piece/rhosgfx) set (CC0), recoloured.
- Opening names from [lichess-org/chess-openings](https://github.com/lichess-org/chess-openings) (CC0).
- Fonts: Montserrat and Roboto (SIL Open Font License).
- Badge symbols from Google's Material icons (Apache 2.0).

SonderChess is not affiliated with or endorsed by Chess.com.

## License

GPL-3.0. See [LICENSE](LICENSE).

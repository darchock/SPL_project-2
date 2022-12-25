package bguspl.set.ex;

public enum PlayerState {
    /*
        * PlayerState.Playing: player has [1,3] tokens in hand
        * PlayerState.PlayingAfterPunishment: player has 3 tokens placed on illegal set, and he already got punished for it i.e. players has 0 tokens
        * PlayerState.Waiting: player has all 3 tokens on talbe and waiting to be check by the dealer
     */

    Playing,
    PlayingAfterPunishment,
    Waiting
}

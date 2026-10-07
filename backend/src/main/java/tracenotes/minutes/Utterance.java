package tracenotes.minutes;

/** 번호 붙은 발언 하나. no는 1부터 빈틈없이 이어진다. */
public record Utterance(int no, String speaker, String text) {
}

package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.CombinedScore;

import java.util.List;

/** Wire representation of a {@link CombinedScore}. */
public record CombinedScoreDto(String value, String level, List<String> usedSources) {

    public static CombinedScoreDto from(CombinedScore score) {
        return new CombinedScoreDto(
                DecimalStrings.of(score.value()),
                score.level().name(),
                score.usedSources().stream().map(Enum::name).toList());
    }
}

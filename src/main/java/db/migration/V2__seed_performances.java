package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * 데모용 공연 3개 + 공연별 좌석 50석(A~E열 x 1~10번) 시드.
 * SQL 로 쓰면 PostgreSQL(generate_series)과 H2 문법이 달라져서 Java 마이그레이션으로 작성.
 */
public class V2__seed_performances extends BaseJavaMigration {

    private static final Object[][] PERFORMANCES = {
            {"쿠버네티스 필하모닉 신년음악회", "예술의전당 콘서트홀", "2027-01-15T19:30:00+09:00", 88000},
            {"뮤지컬 <롤링 업데이트>", "블루스퀘어 신한카드홀", "2027-02-03T20:00:00+09:00", 132000},
            {"인디밴드 카나리 단독공연", "홍대 롤링홀", "2027-02-21T18:00:00+09:00", 55000},
    };
    private static final String[] ROWS = {"A", "B", "C", "D", "E"};
    private static final int SEATS_PER_ROW = 10;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        try (PreparedStatement insertPerformance = conn.prepareStatement(
                "INSERT INTO performance (title, venue, starts_at, price) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement insertSeat = conn.prepareStatement(
                     "INSERT INTO seat (performance_id, seat_no, status) VALUES (?, ?, 'AVAILABLE')")) {

            for (Object[] p : PERFORMANCES) {
                insertPerformance.setString(1, (String) p[0]);
                insertPerformance.setString(2, (String) p[1]);
                insertPerformance.setTimestamp(3, Timestamp.from(OffsetDateTime.parse((String) p[2]).toInstant()));
                insertPerformance.setInt(4, (Integer) p[3]);
                insertPerformance.executeUpdate();

                long performanceId;
                try (ResultSet keys = insertPerformance.getGeneratedKeys()) {
                    keys.next();
                    performanceId = keys.getLong(1);
                }

                for (String row : ROWS) {
                    for (int n = 1; n <= SEATS_PER_ROW; n++) {
                        insertSeat.setLong(1, performanceId);
                        insertSeat.setString(2, row + n);
                        insertSeat.addBatch();
                    }
                }
                insertSeat.executeBatch();
            }
        }
    }
}

package parking.frontend;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static parking.frontend.ParkingData.*;

public final class ParkingApp {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final Color BACKGROUND = new Color(241, 245, 248);
    private static final Color INK = new Color(21, 41, 58);
    private static final Color ERROR = new Color(163, 36, 54);

    private final ParkingApi api;
    private final JLabel status = new JLabel("메뉴에서 작업을 선택하세요.");
    private final List<JButton> buttons = new ArrayList<>();

    private ParkingApp(ParkingApi api) {
        this.api = api;
    }

    public static void main(String[] args) {
        String configuredUrl = System.getenv("PARKING_API_URL");
        String url = args.length > 0 ? args[0]
                : configuredUrl == null || configuredUrl.isBlank() ? "http://localhost:8080" : configuredUrl;
        ParkingApi api = new ParkingApi(URI.create(url));
        String mode = args.length > 1 ? args[1] : "prototype";
        if (!List.of("prototype", "srs").contains(mode) || args.length > 2) {
            throw new IllegalArgumentException("실행 인수는 서버 주소와 prototype 또는 srs 모드입니다.");
        }
        SwingUtilities.invokeLater(() -> new ParkingApp(api).show(url, mode.equals("prototype")));
    }

    private void show(String url, boolean prototype) {
        JFrame frame = new JFrame("주차장 관리 시스템");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 5));
        header.setOpaque(false);
        JLabel title = new JLabel("주차장 관리 시스템");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24));
        title.setForeground(INK);
        header.add(title);
        header.add(new JLabel("API 서버  " + url + (prototype ? "  ·  현재 백엔드" : "  ·  작업 명세서 API")));

        JTabbedPane menus = new JTabbedPane();
        if (prototype) {
            PrototypeParkingApi connected = new PrototypeParkingApi(api);
            menus.addTab("일반 사용자", prototypeUser(connected));
            menus.addTab("관리자", prototypeAdmin(connected));
        } else {
            menus.addTab("일반 사용자", userTabs());
            menus.addTab("관리자", adminTabs());
        }

        status.setForeground(INK);
        status.putClientProperty("html.disable", true);
        root.add(header, BorderLayout.NORTH);
        root.add(menus, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(900, 530));
        frame.setSize(960, 620);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JPanel prototypeUser(PrototypeParkingApi connected) {
        JTextField entryPlate = new JTextField(13);
        JComboBox<VehicleType> vehicleType = new JComboBox<>(VehicleType.values());
        JTextField exitPlate = new JTextField(13);
        JTextArea result = output();
        JButton entry = button("입차 등록", () -> {
            String plate = Input.plate(entryPlate.getText(), false);
            VehicleType type = (VehicleType) vehicleType.getSelectedItem();
            run(() -> connected.enter(plate, type), ticket -> {
                showText(result, """
                        입차 등록 완료

                        차량 번호   %s
                        차종        %s
                        배정 구역   %s
                        입차 시각   %s (KST)
                        """.formatted(PrototypeParkingApi.displayText(ticket.plate()),
                        ticket.type(), ticket.zone(), time(ticket.entryTime())));
            }, "입차를 등록했습니다.");
        });
        JButton exit = button("출차 정산", () -> {
            String plate = Input.plate(exitPlate.getText(), false);
            run(() -> connected.exit(plate), receipt -> showText(result, """
                    주차 영수증

                    차량 번호   %s
                    차종        %s
                    주차 구역   %s
                    주차 시간   %d분 %d초
                    최종 요금   %s
                    """.formatted(PrototypeParkingApi.displayText(receipt.plate()), receipt.type(),
                    receipt.zone(), receipt.seconds() / 60, receipt.seconds() % 60, won(receipt.fee()))),
                    "출차를 정산했습니다.");
        });
        JPanel forms = new JPanel(new GridLayout(0, 1));
        forms.add(row(label("입차 차량 번호", entryPlate), entryPlate,
                label("차종", vehicleType), vehicleType, entry));
        forms.add(row(label("출차 차량 번호", exitPlate), exitPlate, exit));
        forms.add(row(new JLabel("현재 서버는 기본 요금 정산을 지원합니다. 정기권·할인은 연동 준비 중입니다.")));
        return panel(forms, new JScrollPane(result));
    }

    private JPanel prototypeAdmin(PrototypeParkingApi connected) {
        Grid zones = new Grid("구역", "주차 중 차량(대)");
        JButton refresh = button("현황 조회", () -> run(connected::zones, summary -> {
            zones.clear();
            zones.add(ZoneType.GENERAL, summary.general());
            zones.add(ZoneType.LARGE, summary.large());
            zones.add(ZoneType.EV_CHARGING, summary.evCharging());
            zones.add("전체", summary.activeCount());
        }, "구역별 주차 대수를 조회했습니다."));
        return panel(row(refresh, new JLabel("면별 상태·차량 검색·이용 내역은 연동 준비 중입니다.")),
                zones.scroll);
    }

    private JTabbedPane userTabs() {
        JTabbedPane tabs = new JTabbedPane();
        JTextField entryPlate = new JTextField(13);
        JComboBox<VehicleType> vehicleType = new JComboBox<>(VehicleType.values());
        JTextField exitPlate = new JTextField(13);
        JTextField receiptId = new JTextField(30);
        LocalDate today = LocalDate.now(KOREA);
        JTextField passPlate = new JTextField(13);
        JTextField passFrom = new JTextField(today.toString(), 10);
        JTextField passThrough = new JTextField(today.plusMonths(1).toString(), 10);

        JTextArea parkingResult = output();
        JButton entry = button("입차 등록", () -> {
            String plate = Input.plate(entryPlate.getText(), false);
            VehicleType type = (VehicleType) vehicleType.getSelectedItem();
            run(() -> api.enter(plate, type), session -> {
                receiptId.setText(session.sessionId());
                showText(parkingResult, """
                        입차 등록 완료

                        차량 번호   %s
                        차종          %s
                        배정 주차 면  %s
                        입차 시각    %s
                        기록 ID      %s
                        """.formatted(session.plateNumber(), session.vehicleType(), session.spotId(),
                        time(session.enteredAt()), session.sessionId()));
            }, "입차를 등록했습니다.");
        });
        JButton exit = button("출차 정산", () -> {
            String plate = Input.plate(exitPlate.getText(), false);
            run(() -> api.exit(plate), settlement -> {
                receiptId.setText(settlement.session().sessionId());
                showText(parkingResult, receiptText(settlement));
            }, "출차를 정산했습니다.");
        });
        JPanel parkingForms = new JPanel(new GridLayout(0, 1));
        parkingForms.add(row(label("입차 차량 번호", entryPlate), entryPlate,
                label("차종", vehicleType), vehicleType, entry));
        parkingForms.add(row(label("출차 차량 번호", exitPlate), exitPlate, exit));
        tabs.addTab("입·출차", panel(parkingForms, new JScrollPane(parkingResult)));

        JTextArea receiptResult = output();
        JButton receipt = button("영수증 조회", () -> {
            String id = receiptId.getText().trim();
            if (!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException("주차 기록 ID를 UUID 형식으로 입력하세요.");
            }
            run(() -> api.receipt(UUID.fromString(id).toString()),
                    result -> showText(receiptResult, receiptText(result)), "영수증을 조회했습니다.");
        });
        tabs.addTab("영수증", panel(row(label("주차 기록 ID", receiptId), receiptId, receipt),
                new JScrollPane(receiptResult)));

        JTextArea passResult = output();
        JButton pass = button("정기권 등록", () -> {
            String plate = Input.plate(passPlate.getText(), false);
            LocalDate from = Input.date(passFrom.getText());
            LocalDate through = Input.date(passThrough.getText());
            Input.checkRange(from, through);
            run(() -> api.registerPass(plate, from, through), result -> showText(passResult, """
                    정기권 등록 완료

                    차량 번호  %s
                    유효 기간  %s ~ %s (양끝 포함)
                    정기권 ID  %s
                    """.formatted(result.plateNumber(), result.validFrom(), result.validThrough(),
                    result.passId())), "정기권을 등록했습니다.");
        });
        tabs.addTab("정기권", panel(row(label("차량 번호", passPlate), passPlate,
                label("시작일", passFrom), passFrom, label("종료일", passThrough), passThrough, pass),
                new JScrollPane(passResult)));
        return tabs;
    }

    private JTabbedPane adminTabs() {
        JTabbedPane tabs = new JTabbedPane();
        Grid zones = new Grid("구역", "전체", "사용 중", "빈 면");
        Grid spots = new Grid("구역", "주차 면", "상태");
        JButton refresh = button("현황 조회", () -> run(api::zones, result -> {
            zones.clear();
            spots.clear();
            for (Zone zone : result) {
                zones.add(zone.zoneType(), zone.capacity(), zone.occupied(), zone.available());
                for (Spot spot : zone.spots()) {
                    spots.add(zone.zoneType(), spot.spotId(), spot.occupied() ? "사용 중" : "빈 면");
                }
            }
            if (result.isEmpty()) {
                message("등록된 구역이 없습니다.", false);
            }
        }, "구역 현황을 갱신했습니다."));
        tabs.addTab("구역 현황", panel(row(refresh), split(zones, spots)));

        Grid active = new Grid("차량 번호", "차종", "주차 면", "입차 시각 (KST)", "기록 ID");
        JTextField partial = new JTextField(11);
        JTextField minimum = new JTextField(6);
        JButton search = button("차량 검색", () -> {
            String plate = Input.plate(partial.getText(), true);
            Integer minutes = Input.minutes(minimum.getText());
            run(() -> api.active(plate, minutes), sessions -> {
                active.clear();
                for (Session s : sessions) {
                    active.add(s.plateNumber(), s.vehicleType(), s.spotId(), time(s.enteredAt()), s.sessionId());
                }
                if (sessions.isEmpty()) {
                    message("조건에 맞는 주차 차량이 없습니다.", false);
                }
            }, "주차 차량을 조회했습니다.");
        });
        tabs.addTab("주차 차량", panel(row(label("차량 번호 일부", partial), partial,
                label("최소 주차 시간(분)", minimum), minimum, search), active.scroll));

        Grid history = new Grid("차량 번호", "차종", "주차 면", "입차 시각 (KST)",
                "출차 시각 (KST)", "기록 ID");
        Grid totals = new Grid("출차일 (KST)", "출차 건수", "최종 요금 합계");
        JTextField historyPlate = new JTextField(10);
        LocalDate today = LocalDate.now(KOREA);
        JTextField fromField = new JTextField(today.minusDays(6).toString(), 10);
        JTextField toField = new JTextField(today.toString(), 10);
        JButton load = button("이용 내역 조회", () -> {
            LocalDate from = Input.date(fromField.getText());
            LocalDate to = Input.date(toField.getText());
            Input.checkRange(from, to);
            String plate = Input.plate(historyPlate.getText(), true);
            run(() -> api.history(from, to, plate), result -> {
                history.clear();
                totals.clear();
                for (Session s : result.sessions()) {
                    history.add(s.plateNumber(), s.vehicleType(), s.spotId(), time(s.enteredAt()),
                            time(s.exitedAt()), s.sessionId());
                }
                for (DailyTotal total : result.dailyTotals()) {
                    totals.add(total.date(), total.exitCount(), won(total.totalFeeWon()));
                }
                if (result.sessions().isEmpty()) {
                    message("조건에 맞는 출차 기록이 없습니다.", false);
                }
            }, "이용 내역과 일별 집계를 조회했습니다.");
        });
        tabs.addTab("이용 내역", panel(row(label("시작일", fromField), fromField,
                label("종료일", toField), toField, label("차량 번호", historyPlate), historyPlate, load),
                split(history, totals)));
        return tabs;
    }

    private JButton button(String name, Runnable action) {
        JButton button = new JButton(name);
        button.addActionListener(event -> {
            try {
                action.run();
            } catch (IllegalArgumentException e) {
                message(e.getMessage(), true);
            }
        });
        buttons.add(button);
        return button;
    }

    private <T> void run(Callable<T> request, Consumer<T> render, String success) {
        buttons.forEach(button -> button.setEnabled(false));
        message("서버 응답을 기다리는 중입니다.", false);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return request.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    message(success, false);
                    render.accept(result);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ParkingApi.ApiException) {
                        message(cause.getMessage(), true);
                    } else if (cause instanceof IOException) {
                        message("서버와 통신하지 못했습니다. 연결을 확인하고, 등록·정산은 재시도 전에 결과를 조회하세요.", true);
                    } else {
                        message("요청을 처리하는 중 오류가 발생했습니다.", true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message("요청이 중단되었습니다.", true);
                } catch (RuntimeException e) {
                    message("서버 응답을 표시할 수 없습니다. API 응답 형식을 확인하세요.", true);
                } finally {
                    buttons.forEach(button -> button.setEnabled(true));
                }
            }
        }.execute();
    }

    private void message(String text, boolean error) {
        status.setForeground(error ? ERROR : INK);
        status.setText(text);
    }

    private static JPanel row(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        for (JComponent component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static JLabel label(String text, JComponent input) {
        JLabel label = new JLabel(text);
        label.setLabelFor(input);
        return label;
    }

    private static JPanel panel(JComponent form, JComponent results) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(10, 12, 12, 12));
        panel.add(form, BorderLayout.NORTH);
        panel.add(results, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea output() {
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setFont(new JLabel().getFont().deriveFont(15f));
        output.setBorder(new EmptyBorder(14, 16, 14, 16));
        return output;
    }

    private static void showText(JTextArea area, String text) {
        area.setText(text);
        area.setCaretPosition(0);
    }

    private static JSplitPane split(Grid top, Grid bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top.scroll, bottom.scroll);
        split.setResizeWeight(0.6);
        return split;
    }

    private static String time(String timestamp) {
        return OffsetDateTime.parse(timestamp).atZoneSameInstant(KOREA).format(DISPLAY_TIME);
    }

    private static String won(long value) {
        return String.format(Locale.KOREA, "%,d원", value);
    }

    private static String receiptText(Settlement settlement) {
        Session s = settlement.session();
        Receipt r = settlement.receipt();
        String discount = switch (r.discountKind()) {
            case "NONE" -> "없음";
            case "PASS" -> "정기권";
            case "EV" -> "전기차";
            default -> r.discountKind();
        };
        return """
                주차 영수증

                차량 번호    %s
                배정 주차 면  %s
                입차 시각    %s (KST)
                출차 시각    %s (KST)
                총 주차 시간  %d분
                할인 전 요금  %s
                할인 금액    %s (%s)
                최종 요금    %s
                기록 ID      %s
                """.formatted(s.plateNumber(), s.spotId(), time(s.enteredAt()), time(s.exitedAt()),
                r.durationMinutes(), won(r.grossFeeWon()), won(r.discountWon()), discount,
                won(r.totalFeeWon()), s.sessionId());
    }

    private static final class Grid {
        private final DefaultTableModel model;
        private final JScrollPane scroll;

        Grid(String... columns) {
            model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = new JTable(model);
            table.setFillsViewportHeight(true);
            table.setRowHeight(24);
            scroll = new JScrollPane(table);
        }

        void clear() {
            model.setRowCount(0);
        }

        void add(Object... values) {
            model.addRow(values);
        }
    }
}

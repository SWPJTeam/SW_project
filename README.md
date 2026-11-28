# 주차장 관리 시스템

소프트웨어공학 2인 팀 프로젝트. 김원섭은 Java 데스크톱 프론트엔드를, 전승표는 차량·요금 규칙과 백엔드를 담당합니다.

[프로젝트 제안서](PROJECT_PROPOSAL.md) · [작업 명세서](SOFTWARE_REQUIREMENTS_SPECIFICATION.md) · [작업 명세서 이슈 #3](https://github.com/SWPJTeam/SW_project/issues/3)

## 백엔드와 프론트엔드 실행

JDK 21, Maven 3.9 이상, 그래픽 데스크톱 환경이 필요합니다. 저장소 루트의 첫 번째 터미널에서 백엔드를 실행합니다.

```sh
javac -encoding UTF-8 -d backend/bin backend/parking/backend/Vehicle.java backend/parking/backend/ParkingStoreAndServer.java
java -cp backend/bin parking.backend.ParkingStoreAndServer
```

두 번째 터미널에서 프론트엔드를 실행합니다.

```sh
mvn -f frontend/pom.xml verify
mvn -f frontend/pom.xml compile exec:java
```

기본 서버 주소는 `http://localhost:8080`입니다. 주소는 환경 변수 `PARKING_API_URL` 또는 첫 번째 실행 인수로 설정하며 실행 인수가 우선합니다.

## 현재 백엔드에서 사용 가능한 기능

1. **일반 사용자**에서 차량 번호와 차종을 입력하고 **입차 등록**을 누릅니다. 배정 구역과 입차 시각을 확인할 수 있습니다.
2. **관리자 → 현황 조회**에서 일반 차량, 화물차·버스, 전기차 구역별 주차 중인 차량 대수를 확인합니다.
3. **일반 사용자**에서 같은 차량 번호로 **출차 정산**을 누릅니다. 화면에서 주차 시간과 서버가 반환한 최종 요금을 확인합니다.
4. 관리자 현황을 다시 조회하면 출차한 차량이 대수에서 빠집니다.

현재 백엔드는 할인 적용 자격을 판단하는 API가 없어 프론트엔드는 기본 요금(`NONE`)으로 정산을 요청합니다. 면별 현황, 정기권 등록, 영수증 재조회, 차량 검색, 이용 내역은 해당 백엔드 API가 필요합니다. 서버가 재시작되면 현재 프로토타입의 메모리 기록은 초기화됩니다.

### 작업 명세서 모드

작업 명세서의 HTTP API를 제공하는 서버가 준비되면 두 번째 인수에 `srs`를 전달해 정기권, 면별 상태, 이용 내역 등을 포함한 화면을 사용합니다.

```sh
mvn -f frontend/pom.xml compile exec:java -Dexec.args="http://localhost:8080 srs"
```

## 코드 구조와 API 설계

| 구성 | 책임과 설계 이유 |
| --- | --- |
| `ParkingApp` | Swing 화면과 결과 표시를 담당합니다. `SwingWorker`가 네트워크 요청을 처리해 화면이 멈추지 않도록 하고, 응답을 기다리는 동안 요청 버튼을 비활성화해 중복 클릭을 막습니다. |
| `Input` | 차량 번호 정규화와 날짜·검색 입력 검증을 담당합니다. 구역 점유, 정기권 중복, 요금 정책은 백엔드가 판단합니다. |
| `ParkingApi` | UI 동작을 HTTP 요청과 연결합니다. `enter`, `exit`, `receipt`, `registerPass`, `zones`, `active`, `history`가 화면 기능과 일대일로 대응합니다. |
| `PrototypeParkingApi` | 현재 백엔드의 쿼리 방식과 응답 구조를 처리합니다. 구역별 주차 대수와 정산 금액을 서버 값 그대로 표시합니다. |
| `ParkingData` | JSON 요청·응답 필드를 Java 레코드로 정의합니다. 차종 코드와 화면 표시명을 분리해 서버는 `EV`를 받고 화면에는 ‘전기차’가 표시됩니다. |

작업 명세서 모드에서 입차·출차·정기권 등록은 상태를 변경하므로 `POST`, 현황·영수증·검색 조회는 `GET`을 사용합니다. 출차 직후와 영수증 재조회에 동일한 `{session, receipt}` 구조를 사용해 같은 화면 표시 함수를 공유합니다. 서버가 반환한 정산액을 그대로 표시하므로 과거 영수증 금액이 프론트엔드의 규칙 변경으로 달라지지 않습니다.

현재 백엔드 모드는 입차와 출차를 `POST /api/checkin`, `POST /api/checkout`에 쿼리 파라미터로 전송하고, 현황은 `GET /api/zones`로 읽습니다. 현재 서버는 차량 번호의 쿼리 인코딩을 그대로 저장하고 반환하므로 프론트엔드는 두 요청에 같은 UTF-8 인코딩을 사용하고 결과를 화면에 표시할 때만 복원합니다. 서버에서 쿼리를 디코딩하는 표준 처리로 변경하면 이미 디코딩된 응답도 표시할 수 있습니다.

## 검증 및 데모

`mvn verify`는 입력 검증과 로컬 HTTP 테스트 서버를 사용한 프론트엔드 계약 테스트를 실행합니다. 현재 백엔드 시연은 전기차 입차 → 전기차 구역 대수 확인 → 중복 입차 오류 확인 → 출차 영수증 확인 → 관리자 현황에서 대수 감소 확인 순서로 진행할 수 있습니다.

## 서버 실행하는 법

0. IntelliJ에서 File → Open → 이 프로젝트의 `build.gradle` 선택 (그레이들 프로젝트로 자동 인식됨)
1. MySQL 설치
2. `test/00-schema-check.sql` 내용을 MySQL 터미널에 붙여넣기 (테이블 생성)
3. `src/main/resources/application-example.yml`을 복사해서
   같은 폴더에 `application.yml`로 저장하고, 비밀번호만 본인 걸로 채우기
4. `ServerApplication` 실행
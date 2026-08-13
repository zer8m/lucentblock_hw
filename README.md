React + TypeScript + Vite
이 템플릿은 Vite 환경에서 HMR(모듈 모듈 교체) 및 일부 Oxlint 규칙을 적용하여 React를 실행할 수 있는 최소한의 환경을 제공합니다.

현재 두 가지 공식 플러그인을 사용할 수 있습니다:

@vitejs/plugin-react: Oxc를 사용

@vitejs/plugin-react-swc: SWC를 사용

React Compiler
React Compiler는 개발 및 빌드 성능에 영향을 줄 수 있어 이 템플릿에서는 기본적으로 활성화되어 있지 않습니다. 이를 추가하려면 관련 공식 문서를 참고하세요.

Oxlint 설정 확장하기
프로덕션용 애플리케이션을 개발하는 경우, oxlint-tsgolint를 설치하고 .oxlintrc.json 파일에 아래와 같이 typeAware 규칙을 설정하는 것을 권장합니다:
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

전체 규칙 및 카테고리 목록은 Oxlint 규칙 문서를 확인하세요.(https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

# 그림 원본과 다시 만드는 법

`../` 폴더의 PNG 그림을 만든 원본입니다. 그림을 고치고 싶을 때만 보면 됩니다. (macOS와 Google Chrome 기준)

| 그림 | 원본 파일 |
|---|---|
| 01 전체 구성도 | `전체구성도.html` |
| 07 개발과 배포 환경 | `개발과배포환경.html` |
| 02~06 순서도 | `02-요청처리순서.mmd` ~ `06-글작성과이미지.mmd` |

## 1. HTML 그림 (01, 07)

원본 `.html`을 고친 뒤, 이 폴더에서 크롬으로 캡처합니다.

```bash
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=1500,860 --screenshot="$PWD/../01-전체구성도.png" "file://$PWD/전체구성도.html"
"$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=1500,640 --screenshot="$PWD/../07-개발과배포환경.png" "file://$PWD/개발과배포환경.html"
```

## 2. 순서도 (02~06)

Mermaid 도구(`mermaid-cli`)로 `.mmd`를 그림으로 바꿉니다. 이 폴더의 `mermaid.json`(색과 글꼴)과 `puppeteer.json`(크롬 위치)을 씁니다.

```bash
npm i @mermaid-js/mermaid-cli          # 처음 한 번만
for f in 0[2-6]-*.mmd; do
  npx mmdc -i "$f" -o "../${f%.mmd}.png" -s 2 -b white -p puppeteer.json -c mermaid.json
done
```

## 3. 바꾸고 나면

- `06-아키텍처-그림.md`의 "그림 원본 보기" 안의 Mermaid 코드도 같이 고칩니다.
- 직접 하기 어려우면 Claude에게 "그림을 이렇게 고쳐서 다시 만들어 줘"라고 요청하세요.

# Pretendard 1.3.9

2026-10-06 사용자 요청으로 iOS·Android·웹 글꼴을 Pretendard로 통일했다. 이 폴더가 원본이며 각 플랫폼은 여기서 복사한다. 실제로 쓰는 파일만 둔다.

- 출처: npm `pretendard` 1.3.9 (`https://registry.npmjs.org/pretendard/-/pretendard-1.3.9.tgz`, integrity `sha512-PaQAADyLY5v4kYFwkpSJHbSSYIkiriY/1xXw75TKoZ9UQQqeU+tvP05yTdZAWibiIYoo8ZKtRv8PM7w0IaywSw==` 일치 확인).
- 라이선스: SIL Open Font License 1.1, [OFL.txt](OFL.txt). npm 묶음에 라이선스 파일이 없어 같은 버전 태그의 `https://github.com/orioncactus/pretendard/blob/v1.3.9/LICENSE`를 복사했다.
- 고른 형식(앱 크기 비교, 2026-10-06 측정): Android는 APK가 글꼴을 압축 저장하므로 가변 글꼴 1개(+3,242,600 B, 정적 4굵기는 +4,534,359 B). iOS는 설치 크기가 작은 정적 3굵기(설치 +4,734,716 B, 압축 +3,192,824 B; 가변은 설치 +6,739,336 B). 웹은 굵기 전체를 담는 가변 다이나믹 서브셋(92개 woff2, 정적 3굵기 서브셋 합 3.50 MB 대비 2.96 MB).

| 파일 | SHA-256 | 사용처(복사본) |
| --- | --- | --- |
| `PretendardVariable.ttf` | `3090ccde0442bb347aa7685d9ba8b17436a60682df6e8f92a9a670de14056e22` | Android `app/src/main/res/font/pretendard_variable.ttf` (400·500·600·700) |
| `Pretendard-Regular.otf` | `3ffbacde6ab8411f1d2db54bb9b1f0b3ee2a738932033722cf0388c06aed1c93` | iOS `Resources/Fonts/` |
| `Pretendard-SemiBold.otf` | `c89bc43027dc7cde5726e96223376f8eec09302b2fc1f8147fd5b57cfc376118` | iOS `Resources/Fonts/` |
| `Pretendard-Bold.otf` | `2e91915fab54df71cc9598ebf608b2bdb54c6fe3c066ac61dff0bc44fca71cc7` | iOS `Resources/Fonts/` |
| `web/woff2-dynamic-subset/*.woff2` (92개, 이름순 연결) | `9bb067166c52c8d1afce56951c1a107e2a09555281e8dbcf0e67329200d0e350` | 웹 `public/fonts/pretendard/woff2-dynamic-subset/` |
| `web/pretendardvariable-dynamic-subset.css` | `2973bcae80262dcb630cfb793fbf6af29bd986c769ee54953fb3e5b3e32323ca` | 웹 `src/shared/ui/pretendard.css`(파일 경로를 `/fonts/pretendard/woff2-dynamic-subset/`로 바꾼 파생본) |

복사본은 모두 위 값과 같다(웹 CSS 파생본 제외). 원본을 바꾸면 복사본도 함께 바꾸고 이 표를 갱신한다.

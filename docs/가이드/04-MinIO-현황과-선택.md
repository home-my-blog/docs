# 04. MinIO의 현황과 우리의 선택

> **한 줄 요약:** 글에 올린 이미지를 보관할 저장소로 MinIO를 쓰기로 했습니다. 다만 **2026-04에 MinIO 공개판 저장소가 보관(archived)되어** 새 버전과 공식 이미지가 더 나오지 않습니다. 그래서 **개발용으로 쓰고, 언제든 서버 디스크 저장으로 바꿔 끼울 수 있게** 설계합니다.
> 상태: **가안**입니다. (아래 날짜와 내용은 2026-10-02에 웹 검색으로 확인한 결과이며, 직접 확인하려면 [github.com/minio/minio](https://github.com/minio/minio)의 archived 표시를 보세요.)
> 관련 파일: [03-로컬환경-도커컴포즈-사용법.md](03-로컬환경-도커컴포즈-사용법.md), [05-배포-준비.md](05-배포-준비.md), [상세/05-소통-부가.md](../상세/05-소통-부가.md)
> 읽는 순서는 [00-목차.md](00-목차.md)를 봅니다.

## 1. MinIO가 무엇이고 왜 필요한가

- **하는 일:** 이미지 같은 **파일을 보관**하는 저장소 서버입니다. 파일을 올리면 주소를 돌려줍니다. 폴더 역할을 하는 단위를 **버킷**이라고 부릅니다.
- **우리 프로젝트에서:** 글에 올리는 이미지(5MB 이하, 글당 10장 — 요구사항 CF-22)를 보관합니다.
- **DB에 넣지 않는 이유:** 이미지 파일은 크기가 커서 DB에 넣으면 느려집니다. 그래서 **파일은 저장소에, DB에는 주소만** 적습니다.

## 2. 지금 상태 (검색 결과 기준)

| 시기 | 일어난 일 |
|---|---|
| 2025-10경 | 공식 Docker 이미지와 실행 파일 배포를 중단. 마지막으로 나온 이미지는 `RELEASE.2025-09-07`이고 **고위험 보안 취약점이 패치되지 않았다**고 알려짐 |
| 2025-12-03 | 공개 저장소가 유지보수 모드로 바뀜 |
| 2026-04-25 | 저장소가 보관(archived)되어 **읽기 전용**이 됨 |
| 현재 | 공개판은 **소스 코드로만** 배포됨. 쓰려면 직접 빌드해야 함. 라이선스는 AGPLv3 그대로(오픈소스) |

쉽게 말해 **프로그램은 돌아가지만, 더는 관리되지 않고 설치도 예전만큼 쉽지 않습니다.**

## 3. 우리의 선택과 이유

| 항목 | 결정 |
|---|---|
| 쓸 것인가 | **쓴다 (가안).** MinIO로 객체 저장소(S3 방식)를 배우는 것도 목적에 포함 |
| 배우는 내용이 쓸모 있는 이유 | 파일을 올리고 주소를 받는 개념은 **AWS S3 같은 S3 호환 저장소에서 똑같이** 쓰임 |

## 4. 위험을 줄이는 방법

1. **MinIO 전용 SDK 대신 S3 표준 방식**(AWS SDK for Java v2)으로 코드를 만든다. 배운 것이 다른 저장소에도 통한다.
2. 저장 코드를 **`ImageStorage` 인터페이스 하나**로 감싼다. MinIO가 막히면 서버 디스크 구현으로 바꿔 끼운다.
3. **내 컴퓨터(Docker Compose)에서만** 돌린다. 버전을 고정하고, 외부에 포트를 열지 않는다.
4. 문서에 위험을 남긴다. (이 문서)

## 5. 다른 선택지

| 선택지 | 설명 | 이번 프로젝트 |
|---|---|---|
| **서버 디스크** | 서버 컴퓨터의 폴더에 저장하고 DB에는 주소만 적음. 설치할 것이 없음 | 바꿔 끼울 때의 1순위 후보 |
| SeaweedFS | Apache-2.0 라이선스, 활발히 관리됨. MinIO와 가장 비슷한 대체품 | 쓸 수 있지만 설치·설정이 늘어남 |
| Garage | 가볍고 실행 파일 하나. AGPLv3 | 우리 규모에는 과함 |
| RustFS | 공식 문서가 "운영 환경에서는 쓰지 말라"고 명시한 알파 단계 | 쓰지 않음 |

## 6. MinIO를 안 쓰게 되면

| 바뀌는 것 | 안 바뀌는 것 |
|---|---|
| 저장 담당 코드 한 군데 | 요구사항(CF-22: 5MB 이하, 글당 10장) |
| Compose의 `minio` 블록 삭제 | 마크다운과 이미지 업로드 기능 |
| 문서의 구현 방식 몇 줄 | DB에는 이미지 주소만 저장하는 구조 |

## 7. 정하지 못한 것

- 어떤 이미지(고정 버전, 소스 빌드, 포크)로 띄울지: 구현 때 확인
- 배포할 때 MinIO를 쓸지, 서버 디스크로 바꿀지: [05-배포-준비.md](05-배포-준비.md)

Sources:
- [MinIO's community edition is archived. What still runs in 2026](https://stormdevelopments.ca/blog/minio-s-community-edition-is-archived-what-still-runs-in-2026/)
- [MinIO Ends Docker Images: What You Need to Know](https://algustionesa.com/minio-ends-docker-images-what-you-need-to-know/)
- [MinIO Just Got Archived. Here's What Self-Hosters Are Running Instead](https://pinggy.io/blog/minio_archived_self_hosted_s3_alternatives/)
- [RustFS vs SeaweedFS vs Garage: Which MinIO Alternative Should You Pick?](https://blog.elest.io/rustfs-vs-seaweedfs-vs-garage-which-minio-alternative-should-you-pick/)
- [GitHub - minio/minio](https://github.com/minio/minio)

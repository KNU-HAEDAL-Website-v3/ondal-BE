# infra/judge0 - 채점 엔진(Judge0 CE 1.13.1) 배치 파일

> 설계·조사·절차의 원본은 docs 레포 [judge/infra.md](https://github.com/KNU-HAEDAL-Website-v3/ondal-docs/blob/main/docs/judge/infra.md). 이 폴더 = 서버에 올릴 파일의 단일 원천

## 파일

| 파일 | 용도 |
|---|---|
| `docker-compose.yml` | Judge0 공식 1.13.1 compose 와 같은 구조(server · workers · db · redis) |
| `judge0.conf.example` | 우리 설정값 - 복사해 `judge0.conf` 로 만들고 비밀값 3개(`AUTHN_TOKEN`, `REDIS_PASSWORD`, `POSTGRES_PASSWORD`)와 `SECRET_KEY_BASE` 채움 |
| `judge0.conf` | **gitignore** - 서버(VM)에만 존재 |

## 어디서 도는가 - 해달 서버 VM 안 (2026-09-14 조사)

- 호스트(Ubuntu 26.04, 커널 7.0, systemd 259)는 cgroup v2 전용이고 커널에 legacy memory cgroup(`CONFIG_MEMCG_V1`)이 없다 → Judge0 1.13.1(isolate 1.8.1, cgroup v1 필수)은 **호스트에서 못 돈다**
- 그래서 multipass 로 Ubuntu 22.04 VM(2 vCPU · 2GB · 25GB)을 만들고 그 안에서 이 compose 를 띄운다. 절차는 infra.md 4절 - 요약:

```bash
# 호스트 (sudo)
sudo snap install multipass
multipass launch 22.04 --name judge0 --cpus 2 --memory 2G --disk 25G
multipass transfer docker-compose.yml judge0.conf.example judge0:/home/ubuntu/
multipass shell judge0

# VM 안
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu
sudo sed -i 's/^GRUB_CMDLINE_LINUX=""/GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0"/' /etc/default/grub
sudo update-grub && sudo reboot
# (재접속) stat -fc %T /sys/fs/cgroup/  → tmpfs 이면 cgroup v1
cd ~ && cp judge0.conf.example judge0.conf && nano judge0.conf   # <...> 채움
docker compose up -d db redis && sleep 10 && docker compose up -d
docker compose logs -f server   # "Listening on http://0.0.0.0:2358"
```

- 확인·스모크·BE 연결(`.env` 의 `JUDGE0_URL`, `JUDGE0_TOKEN`, `ONDAL_JUDGE_ENGINE=judge0`)은 infra.md 3~6절

## 호스트에서 바로 띄우면 안 되는 이유 (기록)

- `docker compose up` 은 되지만 모든 제출이 `Internal Error`(13) - workers 로그에 `Failed to create control group /sys/fs/cgroup/memory/box-0/`
- GRUB `systemd.unified_cgroup_hierarchy=0` 도 무효 - systemd 259 는 v1 모드가 없고 커널에 memory v1 컨트롤러가 없음
- upstream 의 cgroup v2 대응(PR judge0/judge0#599, isolate 2.6)이 릴리스되면 VM 을 걷고 이 compose 를 호스트로 옮긴다 - 파일 위치는 그대로

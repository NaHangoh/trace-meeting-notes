// PreToolUse 훅 (Bash, PowerShell). 종료 코드 2 = 차단.
// 주의: 명령 문자열 검사는 우회가 가능하다 (변수, 스크립트 파일, sh -c 등).
// 실제 경계는 샌드박스와 "AI 손이 닿는 곳에 진짜 비밀값을 두지 않는 것"이다. docs/THREAT-MODEL.md 참고.
const { execSync } = require("child_process");

let input = "";
process.stdin.on("data", (c) => (input += c));
process.stdin.on("end", () => {
  let data;
  try {
    data = JSON.parse(input);
  } catch {
    process.exit(0);
  }
  const cmd = String((data.tool_input && data.tool_input.command) || "");
  const block = (why) => {
    console.error("차단: " + why + "\n명령: " + cmd);
    process.exit(2);
  };

  const rules = [
    // 되돌릴 수 없는 git 작업
    { re: /\bgit\s+push\b/, why: "git push는 사용자가 직접 합니다." },
    { re: /\bgit\s+reset\s+--hard\b/, why: "강제 리셋은 금지입니다." },
    { re: /\bgit\s+(remote|tag\s+-d|filter-branch|filter-repo|reflog\s+expire|gc\s+--prune)\b/, why: "원격·태그·이력 변경은 사용자가 직접 합니다." },
    { re: /\bgit\s+clean\b/, why: "git clean은 추적되지 않는 파일을 지웁니다. 지울 파일을 사용자에게 먼저 알리세요." },
    { re: /\bgit\s+(checkout|restore)\s+(--\s+)?\.(\s|$)/, why: "작업 중 변경 전체를 버리는 명령입니다." },
    { re: /\bgit\s+branch\s+-D\b|\bgit\s+stash\s+(drop|clear)\b/, why: "브랜치·stash 삭제는 사용자가 직접 합니다." },
    { re: /--no-verify\b/, why: "커밋 훅 건너뛰기는 금지입니다." },
    // 대량 삭제 (Linux, macOS, Windows)
    { re: /\brm\s+(-[a-zA-Z]*[rR][a-zA-Z]*|--recursive)\b/, why: "재귀 삭제는 금지입니다. 지울 파일 목록을 사용자에게 먼저 알리세요." },
    { re: /Remove-Item\b[^\n]*-Recurse|\brmdir\s+\/s|\brd\s+\/s|\bdel\s+\/s|\bformat\s+[a-z]:/i, why: "재귀 삭제는 금지입니다." },
    { re: /\bfind\b[^\n]*\s-delete\b/, why: "find -delete는 금지입니다." },
    // 비밀값 노출
    { re: /\.env(\s|$|["'])/, why: ".env 파일을 명령으로 다룰 수 없습니다. 값이 필요하면 사용자에게 요청하세요." },
    { re: /ANTHROPIC_API_KEY|\bprintenv\b|(^|[;&|]\s*)env\s*($|\|)|(^|[;&|]\s*)set\s*($|\|)|Get-ChildItem\s+env:|\bgci\s+env:|\bls\s+env:|\$env:[A-Z_]*(KEY|TOKEN|SECRET)/i, why: "환경 변수(비밀값) 출력은 금지입니다." },
    // AI 환경 자체를 바꾸는 명령
    { re: /\.claude[\/\\](settings|hooks)|\.mcp\.json|\.git[\/\\](hooks|config)/, why: "AI 권한 설정, 훅, git 훅은 사용자가 직접 수정합니다." },
    { re: /dangerously-skip-permissions|disableAllHooks|bypassPermissions/, why: "권한 검사를 끄는 옵션은 사용할 수 없습니다." },
    { re: /\bclaude\s+mcp\s+add\b/, why: "MCP 서버 추가는 사용자가 검토 후 직접 합니다." },
    // 공개 저장소 정책
    { re: /Co-Authored-By|Generated with \[?Claude|claude\.ai\/code/i, why: "커밋·PR에 AI 서명을 넣지 않습니다." },
    // 원격 스크립트 실행
    { re: /(curl|wget|iwr|Invoke-WebRequest)\b[^\n]*\|\s*(sh|bash|zsh|iex|Invoke-Expression|node|python)/i, why: "내려받은 스크립트를 바로 실행할 수 없습니다." },
  ];
  for (const r of rules) if (r.re.test(cmd)) block(r.why);

  // 커밋 직전 스테이징 내용 검사: 비밀값, 비밀 파일
  if (/\bgit\s+commit\b/.test(cmd)) {
    const cwd = process.env.CLAUDE_PROJECT_DIR || process.cwd();
    let names = "", diff = "";
    try {
      names = execSync("git diff --cached --name-only", { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
      diff = execSync("git diff --cached -U0", { cwd, encoding: "utf8", maxBuffer: 20 * 1024 * 1024, stdio: ["ignore", "pipe", "ignore"] });
    } catch {
      // git 저장소가 아니거나 스테이징이 없으면 git이 알아서 실패한다
    }
    const badFile = names.split(/\r?\n/).find((f) => (/(^|\/)\.env($|\.)/.test(f) && !/\.env\.example$/.test(f)) || /\.(pem|key|p12|jks)$/.test(f) || /application-secret/.test(f));
    if (badFile) block("비밀값 파일이 스테이징되어 있습니다: " + badFile);

    const added = diff.split(/\r?\n/).filter((l) => l.startsWith("+") && !l.startsWith("+++")).join("\n");
    const secretRes = [
      /sk-ant-[A-Za-z0-9_\-]{20,}/,
      /AKIA[0-9A-Z]{16}/,
      /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
      /gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}/,
      /xox[abposr]-[A-Za-z0-9-]{10,}/,
      /AIza[0-9A-Za-z_\-]{35}/,
      /(api[_-]?key|secret|password|passwd|token)["']?\s*[:=]\s*["'][^"'\s$<{]{12,}["']/i,
    ];
    if (secretRes.some((re) => re.test(added))) {
      block("스테이징된 변경에 비밀값으로 보이는 문자열이 있습니다. 값을 지우고 .env.example 변수 이름으로 바꾸세요. (값은 출력하지 않음)");
    }
  }
  process.exit(0);
});

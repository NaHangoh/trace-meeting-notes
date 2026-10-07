// PreToolUse 훅 (Read, Write, Edit, MultiEdit, NotebookEdit, Grep, Glob)
// 종료 코드 2 = 차단. stderr 메시지가 Claude에게 전달된다.
// 막는 것: 비밀값 파일 접근, AI 설정·훅 자기 수정, eval 정답 수정, 테스트 건너뛰기 추가
let input = "";
process.stdin.on("data", (c) => (input += c));
process.stdin.on("end", () => {
  let data;
  try {
    data = JSON.parse(input);
  } catch {
    process.exit(0);
  }
  const tool = data.tool_name || "";
  const ti = data.tool_input || {};
  const norm = (s) => String(s || "").replace(/\\/g, "/");
  const block = (why, p) => {
    console.error("차단: " + why + (p ? " (" + p + ")" : ""));
    process.exit(2);
  };

  // 검사할 경로: 파일 경로 + Grep/Glob의 path, glob, pattern 인자
  const paths = [ti.file_path, ti.notebook_path, ti.path].map(norm).filter(Boolean);
  const globs = [ti.glob, tool === "Glob" ? ti.pattern : ""].map(norm).filter(Boolean);
  const all = paths.concat(globs);

  // 1) 비밀값
  const isEnv = (p) => /(^|\/)\.env($|\.)/.test(p) && !/\.env\.example$/.test(p);
  const isSecret = (p) => /secret|credential|\.pem$|\.key$|id_rsa|\.p12$|\.jks$/i.test(p);
  for (const p of all) {
    if (isEnv(p)) block(".env 파일은 읽거나 수정할 수 없습니다. .env.example의 변수 이름만 사용하세요.", p);
    if (isSecret(p)) block("비밀값 파일로 보이는 경로입니다.", p);
  }

  const isWrite = ["Write", "Edit", "MultiEdit", "NotebookEdit"].includes(tool);
  if (!isWrite) process.exit(0);

  for (const p of paths) {
    // 2) AI가 자기 권한·훅을 고치지 못하게 한다 (사람이 직접 수정)
    if (/(^|\/)\.claude\/(settings(\.local)?\.json|hooks\/)/.test(p) || /(^|\/)\.mcp\.json$/.test(p)) {
      block("AI 권한 설정과 훅은 사용자가 직접 수정합니다. 바꿔야 하면 이유와 변경안을 제안만 하세요.", p);
    }
    if (/(^|\/)\.git\//.test(p)) block(".git 내부(훅, 설정)는 수정할 수 없습니다.", p);

    // 3) eval 정답은 사람이 만든다
    if (/eval\/cases\/.+\/(expected|planted)/.test(p)) {
      block("eval 정답 데이터는 사용자가 직접 수정합니다. 결과를 맞추려고 정답을 바꾸지 마세요.", p);
    }
  }

  // 4) 테스트를 통과시키려고 건너뛰기를 넣는 것 차단
  const p0 = paths[0] || "";
  const isTest = /(^|\/)(src\/test\/|__tests__\/)|\.(test|spec)\.[jt]sx?$|Test\.java$|Tests\.java$/.test(p0);
  if (isTest) {
    const newText = [ti.content, ti.new_string]
      .concat((ti.edits || []).map((e) => e.new_string))
      .filter(Boolean)
      .join("\n");
    const oldText = [ti.old_string].concat((ti.edits || []).map((e) => e.old_string)).filter(Boolean).join("\n");
    const skipRe = /@Disabled\b|@Ignore\b|\b(it|test|describe)\.(skip|only|todo)\s*\(|\bx(it|test|describe)\s*\(|assumeTrue\s*\(\s*false/;
    if (skipRe.test(newText) && !skipRe.test(oldText)) {
      block("테스트에 건너뛰기(@Disabled, .skip, .only 등)를 추가할 수 없습니다. 실패 원인을 보고하고 사용자 확인을 받으세요.", p0);
    }
  }
  process.exit(0);
});

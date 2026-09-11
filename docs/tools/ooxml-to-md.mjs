/**
 * docs/ 의 원본 docx · xlsx 를 마크다운(REQUIREMENTS.md · PROGRAMS.md)으로 다시 만든다.
 *
 *   node docs/tools/ooxml-to-md.mjs        (저장소 루트에서)
 *
 * 왜 필요한가 — 원본은 OOXML(zip) 이라 grep 도 diff 도 되지 않는다. 요구사항을
 * 코드와 나란히 검색하려면 평문 사본이 있어야 하고, 원본이 개정될 때마다 사본을
 * 손으로 다시 만들면 반드시 어긋난다. 그래서 변환을 스크립트로 고정한다.
 *
 * 사본은 사람이 고치지 않는다. 원본 docx · xlsx 를 고치고 이 스크립트를 다시 돌린다.
 *
 * 압축 해제에 PowerShell 의 Expand-Archive 를 쓰므로 Windows 를 전제한다.
 * 다른 OS 라면 unzip 명령으로 바꾸면 된다.
 */
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { execFileSync } from 'node:child_process'

const DOCS = 'docs'
const DOCX = path.join(DOCS, 'WMS_온라인_요구사항분석정의서_v2.0.docx')
const XLSX = path.join(DOCS, 'program_list_v0_3_20260911.xlsx')

// ── 공통 ────────────────────────────────────────────────────
const unesc = (s) =>
  s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(+d))
    .replace(/&amp;/g, '&')

/** OOXML 파일을 임시 폴더에 풀고 그 경로를 돌려준다 */
function unzip(file, tag) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `ooxml-${tag}-`))
  // Expand-Archive 는 확장자가 .zip 이 아니면 거부한다
  const zip = path.join(dir, 'src.zip')
  fs.copyFileSync(file, zip)
  execFileSync('powershell', [
    '-NoProfile', '-Command',
    `Expand-Archive -Path '${zip}' -DestinationPath '${dir}' -Force`,
  ])
  return dir
}

/** 연속된 표 행 묶음의 첫 줄 뒤에 마크다운 구분선을 넣는다 */
function withTableSeparators(lines) {
  const out = []
  let prevWasRow = false
  for (const line of lines) {
    const isRow = line.startsWith('|')
    if (isRow && !prevWasRow) {
      out.push(line, '|' + ' --- |'.repeat(Math.max(line.split('|').length - 2, 1)))
    } else {
      if (!isRow && prevWasRow) out.push('')
      out.push(line)
    }
    prevWasRow = isRow
  }
  return out
}

// ── 요구사항 정의서 (docx) ──────────────────────────────────
function convertDocx() {
  const dir = unzip(DOCX, 'req')
  const xml = fs.readFileSync(path.join(dir, 'word', 'document.xml'), 'utf8')

  // <w:p> 단위로 자르고, 표 셀(</w:tc>)이 끝날 때마다 셀을 모아 행으로 낸다
  const raw = []
  let cells = []
  for (const chunk of xml.split(/<w:p[ >]/).slice(1)) {
    const text = unesc([...chunk.matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map((m) => m[1]).join(''))
    if (chunk.includes('</w:tc>')) {
      cells.push(text.trim())
      if (chunk.includes('</w:tr>')) { raw.push('| ' + cells.join(' | ') + ' |'); cells = [] }
    } else if (text.trim()) {
      raw.push(text)
    }
  }

  const body = []
  for (const line of raw) {
    // 목차·표지에는 서식 XML 조각이 그대로 섞여 나온다. 본문에 같은 내용이 다시 나오므로 버린다.
    if (line.includes('<w:')) continue
    if (line.trim() === '목      차') continue
    if (line.startsWith('|')) { body.push(line); continue }

    let m
    if ((m = line.match(/^(\d+)\.\s+(.+)$/))) { body.push(`\n## ${m[1]}. ${m[2].trim()}`); continue }
    if ((m = line.match(/^(\d+\.\d+)\s+(.+)$/))) { body.push(`\n### ${m[1]} ${m[2].trim()}`); continue }
    if ((m = line.match(/^(\d+단계\.)\s+(.+)$/))) {
      body.push(`\n### ${m[1]} ${m[2].trim().replace(/\s{2,}/g, ' ')}`); continue
    }
    if (/^\S.*\[[A-Z]+\]\s+\d+건$/.test(line)) { body.push(`\n### ${line.replace(/\s{2,}/g, ' ')}`); continue }
    if (line.startsWith('·')) { body.push(`- ${line.slice(1).trim()}`); continue }
    if (line.startsWith('※')) { body.push(`> ${line}`); continue }
    body.push(line)
  }

  // 표지 두 줄은 H1 과 겹친다
  while (body.length && !body[0].startsWith('|') && !body[0].startsWith('\n')) body.shift()

  const head = `# WMS 온라인 요구사항 분석 정의서 v2.0

> 이 파일은 \`${path.basename(DOCX)}\` 를 마크다운으로 옮긴 것입니다.
> **원본은 docx 이며 이 파일은 검색·참조용 사본입니다.**
> 손으로 고치지 마세요. 원본을 고친 뒤 \`node docs/tools/ooxml-to-md.mjs\` 로 다시 만듭니다.

`
  fs.writeFileSync(path.join(DOCS, 'REQUIREMENTS.md'), head + withTableSeparators(body).join('\n') + '\n', 'utf8')
  fs.rmSync(dir, { recursive: true, force: true })
  // 기능 요구사항 ID 수 (NFR-* · IFR-* 는 형태가 달라 여기서 세지 않는다)
  return body.filter((l) => /^\| [A-Z]{3,4}-\d{3} /.test(l)).length
}

// ── 프로그램 목록 (xlsx) ────────────────────────────────────
function convertXlsx() {
  const dir = unzip(XLSX, 'prog')
  const xl = path.join(dir, 'xl')

  const shared = [
    ...fs.readFileSync(path.join(xl, 'sharedStrings.xml'), 'utf8').matchAll(/<si>([\s\S]*?)<\/si>/g),
  ].map((m) => unesc([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join('')))

  const names = [
    ...fs.readFileSync(path.join(xl, 'workbook.xml'), 'utf8').matchAll(/<sheet[^>]*name="([^"]*)"/g),
  ].map((m) => unesc(m[1]))

  const colIndex = (ref) =>
    [...ref.match(/^[A-Z]+/)[0]].reduce((n, c) => n * 26 + (c.charCodeAt(0) - 64), 0) - 1

  /**
   * 비율 셀을 백분율로 읽기 좋게 바꾼다 (0.32307692307692304 → 32.3%).
   * 진행 가중치(0.2 · 0.5 · 0.7 · 0.9)는 비율이 아니므로 건드리면 안 된다.
   * 엑셀이 계산해 넣은 비율만 소수점이 길거나 지수표기라, 그걸로 구분한다.
   */
  const pct = (c) =>
    /^\d*\.\d{4,}$/.test(c) || /^\d+(\.\d+)?E-\d+$/i.test(c)
      ? (Number(c) * 100).toFixed(1) + '%'
      : c

  const body = []
  const files = fs.readdirSync(path.join(xl, 'worksheets')).filter((f) => f.endsWith('.xml')).sort()

  files.forEach((f, i) => {
    body.push(`\n## ${names[i] ?? f}`)
    const xml = fs.readFileSync(path.join(xl, 'worksheets', f), 'utf8')
    for (const row of xml.split(/<row[ >]/).slice(1)) {
      const cells = []
      for (const m of row.matchAll(/<c r="([A-Z]+\d+)"([^>]*)>([\s\S]*?)<\/c>/g)) {
        const [, ref, attrs, cell] = m
        const v = (cell.match(/<v>([\s\S]*?)<\/v>/) || [])[1]
        const inline = (cell.match(/<is>[\s\S]*?<t[^>]*>([\s\S]*?)<\/t>/) || [])[1]
        const text = inline != null ? unesc(inline) : v == null ? '' : /t="s"/.test(attrs) ? shared[+v] : v
        cells[colIndex(ref)] = pct((text ?? '').replace(/\s+/g, ' ').trim())
      }
      const filled = Array.from(cells, (c) => c ?? '')
      while (filled.length && filled[filled.length - 1] === '') filled.pop()
      if (!filled.length) continue
      // 요약 시트의 합계 행은 라벨이 병합셀에 들어 있어, 첫 칸에 엑셀 행번호가 새어 나온다.
      // 이 시트들의 실제 수치는 200 을 넘지 않으므로 그보다 큰 앞자리 숫자는 라벨로 바꾼다.
      if (i > 0 && /^\d+$/.test(filled[0]) && Number(filled[0]) > 200) filled[0] = '합계'
      // 한 칸짜리 행은 표가 아니라 소제목이다
      if (filled.filter(Boolean).length === 1 && filled[0]) {
        body.push(filled[0].startsWith('※') ? `\n> ${filled[0]}` : `\n**${filled[0]}**\n`)
        continue
      }
      body.push('| ' + filled.join(' | ') + ' |')
    }
  })

  const head = `# WMS 프로그램 목록 v0.3

> 이 파일은 \`${path.basename(XLSX)}\` 를 마크다운으로 옮긴 것입니다.
> **원본은 xlsx 이며 이 파일은 검색·참조용 사본입니다.**
> 손으로 고치지 마세요. 진행상태는 원본에서 바꾼 뒤 \`node docs/tools/ooxml-to-md.mjs\` 로 다시 만듭니다.
>
> 요약 시트의 합계 행은 원본이 병합셀이라 열이 밀려 나옵니다. 숫자는 상단 표를 기준으로 보세요.

`
  fs.writeFileSync(path.join(DOCS, 'PROGRAMS.md'), head + withTableSeparators(body).join('\n') + '\n', 'utf8')
  fs.rmSync(dir, { recursive: true, force: true })
  // 프로그램 행 수 — 1번 시트의 "순번 | 업무영역" 으로 시작하는 행만 센다
  const AREA = '공통|기준정보|재고|구매·입고|주문·할당|출고·패킹|인터페이스|배송·반품|판매오더·B2B|조회·분석'
  return body.filter((l) => new RegExp(`^\\| \\d+ \\| (${AREA}) \\| `).test(l)).length
}

const reqs = convertDocx()
const progs = convertXlsx()
console.log(`REQUIREMENTS.md  기능 요구사항 ${reqs}건`)
console.log(`PROGRAMS.md      프로그램 ${progs}건`)
// 원본이 스스로 선언한 건수와 대조한다. 어긋나면 변환이 무언가를 흘린 것이다.
if (reqs !== 98) console.warn('  경고: 기능 요구사항이 98건이 아닙니다. 변환 누락을 확인하세요.')
if (progs !== 115) console.warn('  경고: 프로그램이 115건이 아닙니다. 변환 누락을 확인하세요.')

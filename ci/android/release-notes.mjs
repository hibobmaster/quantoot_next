import { execFileSync } from 'node:child_process';

export const versionTag = /^v(\d+\.\d+\.\d+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?)$/;

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim();

export function generateReleaseNotes(tag, repositoryUrl) {
  const prerelease = tag.includes('-');
  const candidates = git('tag', '--merged', 'HEAD').split('\n')
    .filter(name => name !== tag && versionTag.test(name) && (prerelease || !name.includes('-')));
  const previous = candidates.length
    ? git('describe', '--tags', '--abbrev=0', ...candidates.flatMap(name => ['--match', name]), 'HEAD')
    : null;
  const range = previous ? `${previous}..HEAD` : 'HEAD';
  const groups = {
    feat: ['功能改进', []],
    fix: ['问题修复', []],
    perf: ['性能改进', []],
    build: ['构建与打包', []],
    other: ['其他改动', []],
  };

  for (const commit of git('log', '--no-merges', '--format=%H%x09%s', range, '--').split('\n').filter(Boolean)) {
    const [hash, rawSubject] = commit.split('\t', 2);
    const conventional = /^(\w+)(?:\([^)]*\))?!?:\s+(.+)$/.exec(rawSubject);
    const type = conventional?.[1] ?? 'other';
    if (['chore', 'ci', 'docs', 'test', 'style'].includes(type)) continue;
    const subject = (conventional?.[2] ?? rawSubject).replace(/[[\]()*_<>\x60\\]/g, '\\$&');
    const link = `[${hash.slice(0, 7)}](${repositoryUrl}/commit/${hash})`;
    const group = Object.hasOwn(groups, type) ? groups[type] : groups.other;
    group[1].push(`- ${subject} (${link})`);
  }

  const sections = Object.values(groups).filter(([, changes]) => changes.length)
    .map(([heading, changes]) => `### ${heading}\n\n${changes.join('\n')}`);
  const introduction = previous
    ? `[查看 ${previous} 至 ${tag} 的完整变更](${repositoryUrl}/compare/${previous}...${tag})`
    : '这是此仓库的首个版本标签。';
  return ['## 更新日志', introduction, ...(sections.length ? sections : ['此版本没有列出的应用改动。'])].join('\n\n');
}

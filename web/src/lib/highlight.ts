export function highlightJava(src: string): string {
  const escape = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  let s = escape(src)
  s = s.replace(/(\/\/[^\n]*)/g, '<span class="cm">$1</span>')
  s = s.replace(/("(?:\\.|[^"\\])*")/g, '<span class="st">$1</span>')
  const kws = [
    'record','class','interface','enum','public','private','protected',
    'static','final','abstract','new','return','if','else','for','while',
    'do','switch','case','break','continue','throw','throws','try','catch',
    'finally','import','package','extends','implements','this','super',
    'void','true','false','null','var',
  ]
  s = s.replace(new RegExp(`\\b(${kws.join('|')})\\b`, 'g'), '<span class="kw">$1</span>')
  s = s.replace(/\b([A-Z][A-Za-z0-9_]+)\b/g, '<span class="ty">$1</span>')
  s = s.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="nm">$1</span>')
  return s
}

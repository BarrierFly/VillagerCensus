import re
import json
import sys

prof_re = re.compile(r'hashMap\.put\(\s*VillagerProfession\.([A-Z_]+)\s*,')
ctor_re = re.compile(r'new VillagerTrades\.(\w+)\s*\(')

EMERALD = 'minecraft:emerald'
ENCHANTED_BOOK = 'minecraft:enchanted_book'
BOOK = 'minecraft:book'
FILLED_MAP = 'minecraft:filled_map'
COMPASS = 'minecraft:compass'
STEW = 'minecraft:suspicious_stew'

COLORS = sorted(
    ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
     'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'],
    key=len, reverse=True,
)


def balanced(src, i):
    depth = 0
    for j in range(i, len(src)):
        c = src[j]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return src[i + 1:j]
    return ''


def split_args(s):
    args = []
    depth = 0
    cur = ''
    for c in s:
        if c in '([{':
            depth += 1
            cur += c
        elif c in ')]}':
            depth -= 1
            cur += c
        elif c == ',' and depth == 0:
            args.append(cur.strip())
            cur = ''
        else:
            cur += c
    if cur.strip():
        args.append(cur.strip())
    return args


def to_item(token):
    token = token.strip()
    m = re.match(r'(?:Items|Blocks)\.([A-Z0-9_]+)$', token)
    if m:
        return 'minecraft:' + m.group(1).lower()
    return None


def path(item_id):
    return item_id.split(':', 1)[1] if item_id and ':' in item_id else item_id


def strip_color(item_id):
    p = path(item_id)
    for c in COLORS:
        if p.startswith(c + '_'):
            return p[len(c) + 1:]
        if p.endswith('_' + c):
            return p[:-(len(c) + 1)]
    return p


def parse_listing(ctor, args):
    try:
        if ctor == 'EmeraldForItems':
            return (to_item(args[0]), '', [EMERALD])
        if ctor == 'ItemsForEmeralds':
            return (EMERALD, '', [to_item(args[0])])
        if ctor == 'ItemsAndEmeraldsToItems':
            return (to_item(args[0]), EMERALD, [to_item(args[3])])
        if ctor == 'EnchantedItemForEmeralds':
            return (EMERALD, '', [to_item(args[0])])
        if ctor == 'EnchantBookForEmeralds':
            return (EMERALD, BOOK, [ENCHANTED_BOOK])
        if ctor == 'DyedArmorForEmeralds':
            return (EMERALD, '', [to_item(args[0])])
        if ctor == 'TippedArrowForItemsAndEmeralds':
            return (to_item(args[0]), EMERALD, [to_item(args[2])])
        if ctor == 'TreasureMapForEmeralds':
            return (EMERALD, COMPASS, [FILLED_MAP])
        if ctor == 'SuspiciousStewForEmerald':
            return (EMERALD, '', [STEW])
        if ctor == 'EmeraldsForVillagerTypeItem':
            if len(args) >= 4:
                items = ['minecraft:' + n.lower() for _, n in re.findall(r'(Items|Blocks)\.([A-Z0-9_]+)', args[3])]
                if items:
                    return (EMERALD, '', items)
            return None
    except IndexError:
        return None
    return None


def category_id(cost1, cost2, results):
    if len(results) > 1:
        return 'sell_' + strip_color(results[0]) + '_variants'
    result = results[0]
    if cost1 == EMERALD and cost2 == '':
        return 'sell_' + strip_color(result)
    if cost1 == EMERALD and cost2:
        return 'sell_' + strip_color(result) + '_for_' + strip_color(cost2)
    if cost1 != EMERALD and cost2 == '':
        return 'buy_' + strip_color(cost1)
    if cost1 != EMERALD and cost2 == EMERALD:
        return 'buy_' + strip_color(cost1) + '_get_' + strip_color(result)
    return 'trade_' + strip_color(cost1) + '_' + strip_color(cost2) + '_' + strip_color(result)


def parse(src):
    start = src.find('TRADES = Util.make(')
    candidates = [src.find('\n    private static', start), src.find('WANDERING_TRADER_TRADES', start)]
    end = min(x for x in candidates if x != -1)
    region = src[start:end]

    puts = list(prof_re.finditer(region))
    result = {}

    for idx, m in enumerate(puts):
        profession = 'minecraft:' + m.group(1).lower()
        block_end = puts[idx + 1].start() if idx + 1 < len(puts) else len(region)
        block = region[m.end():block_end]

        seen = {}
        for ctor_m in ctor_re.finditer(block):
            ctor = ctor_m.group(1)
            paren = block.find('(', ctor_m.end() - 1)
            args = split_args(balanced(block, paren))
            parsed = parse_listing(ctor, args)
            if not parsed:
                continue
            cost1, cost2, results = parsed
            if not cost1 or any(r is None for r in results):
                continue
            seen[(cost1, cost2, tuple(sorted(results)))] = (cost1, cost2, results)

        # Merge color variants that collapse to the same category id.
        merged = {}
        for cost1, cost2, results in seen.values():
            cid = category_id(cost1, cost2, results)
            entry = merged.setdefault(cid, {'id': cid, 'cost1': set(), 'cost2': set(), 'result': set()})
            entry['cost1'].add(cost1)
            entry['cost2'].update([cost2] if cost2 else [])
            entry['result'].update(results)

        result[profession] = [
            {'id': e['id'], 'cost1': sorted(e['cost1']), 'cost2': sorted(e['cost2']), 'result': sorted(e['result'])}
            for e in merged.values()
        ]

    lib = result.setdefault('minecraft:librarian', [])
    if not any(c['id'] == 'sell_enchanted_book' for c in lib):
        lib.append({'id': 'sell_enchanted_book', 'cost1': [EMERALD], 'cost2': [BOOK], 'result': [ENCHANTED_BOOK]})

    return result


def main():
    src_path, out_path = sys.argv[1], sys.argv[2]
    with open(src_path, encoding='utf-8') as f:
        src = f.read()

    parsed = parse(src)
    out = []
    for profession in sorted(parsed):
        cats = parsed[profession]
        if not cats:
            continue
        out.append({
            'profession': profession,
            'categories': [
                {
                    'id': c['id'],
                    'label': c['id'],
                    'cost1': c['cost1'],
                    'cost2': c['cost2'],
                    'result': c['result'],
                }
                for c in cats
            ],
        })

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    total = sum(len(p['categories']) for p in out)
    print('professions=%d categories=%d' % (len(out), total))
    for p in out:
        print('  %-28s %d' % (p['profession'], len(p['categories'])))


if __name__ == '__main__':
    main()

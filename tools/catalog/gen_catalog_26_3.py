import re
import json
import os
import sys

EMERALD = 'minecraft:emerald'
COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
          'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']

ITEM_COLL_SUFFIX = {
    'Items.WOOL': 'wool',
    'Items.CARPET': 'carpet',
    'Items.BED': 'bed',
    'Items.BANNER': 'banner',
    'Items.DYE': 'dye',
    'Items.DYED_TERRACOTTA': 'terracotta',
    'Items.GLAZED_TERRACOTTA': 'glazed_terracotta',
}


def balanced(s, i):
    depth = 0
    for j in range(i, len(s)):
        c = s[j]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return s[i + 1:j]
    return ''


def balanced_braces(s, i):
    depth = 0
    for j in range(i, len(s)):
        c = s[j]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return s[i + 1:j]
    return ''


def split_args(s):
    args, depth, cur = [], 0, ''
    for c in s:
        if c in '([{':
            depth += 1; cur += c
        elif c in ')]}':
            depth -= 1; cur += c
        elif c == ',' and depth == 0:
            args.append(cur.strip()); cur = ''
        else:
            cur += c
    if cur.strip():
        args.append(cur.strip())
    return args


def camel_to_snake(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def resolve_item(token, subs):
    token = token.strip()
    if token in subs:
        token = subs[token].strip()
    if token.startswith('minecraft:'):
        return token
    m = re.match(r'(Items|Blocks)\.([A-Z0-9_]+)$', token)
    if m:
        return 'minecraft:' + m.group(2).lower()
    m = re.match(r'Items\.(WOOL|DYE|BANNER|DYED_CANDLE)\.([A-Za-z]+)\(\)$', token)
    if m:
        suffix = {'WOOL': 'wool', 'DYE': 'dye', 'BANNER': 'banner', 'DYED_CANDLE': 'candle'}[m.group(1)]
        return 'minecraft:' + camel_to_snake(m.group(2)) + '_' + suffix
    return None


def parse_builder_items(builder, subs):
    start = builder.find('VillagerTrade.builder(')
    if start < 0:
        return None
    args = split_args(balanced(builder, builder.find('(', start)))

    def item_of(expr):
        inner = balanced(expr, expr.find('('))
        return resolve_item(split_args(inner)[0], subs)

    costs = []
    result = None
    for a in args:
        if a.startswith('new TradeCost('):
            costs.append(item_of(a))
        elif a.startswith('new ItemStackTemplate('):
            result = item_of(a)
    if result is None or not costs:
        return None
    return (costs[0], costs[1] if len(costs) > 1 else '', result)


def category_id(cost1, cost2, result):
    nm = lambda i: i.split(':', 1)[1]
    if cost1 == EMERALD and not cost2:
        return 'sell_' + nm(result)
    if cost1 != EMERALD and not cost2:
        return 'buy_' + nm(cost1)
    if cost1 == EMERALD and cost2:
        return 'sell_' + nm(result) + '_for_' + nm(cost2)
    if cost1 != EMERALD and cost2 == EMERALD:
        return 'buy_' + nm(cost1) + '_get_' + nm(result)
    return 'trade_' + nm(cost1) + '_' + nm(cost2) + '_' + nm(result)


def main():
    src_path, tags_dir, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(src_path, encoding='utf-8') as f:
        src = f.read()

    single = {m.group(1): m.group(2) for m in
              re.finditer(r'([A-Z0-9_]+)\s*=\s*resourceKey\(\s*"([^"]+)"\s*\)', src)}
    coll = {m.group(1): (m.group(2), m.group(3)) for m in re.finditer(
        r'([A-Z0-9_]+)\s*=\s*ColorCollection\.NAMES\s*\.map\(color\s*->\s*resourceKey\(\s*"([^"]*)"\s*\+\s*color\s*\+\s*"([^"]*)"\s*\)\s*\)', src)}

    create = {}
    for m in re.finditer(r'private static VillagerTrade (create\w+)\(([^)]*)\)\s*\{', src):
        params = [p.strip().split()[-1] for p in split_args(m.group(2)) if p.strip()]
        body = balanced_braces(src, m.start() + src[m.start():].find('{'))
        create[m.group(1)] = (params, body)

    def resolve_trade(expr, subs):
        expr = expr.strip()
        if 'VillagerTrade.builder(' in expr:
            return parse_builder_items(expr, subs)
        m = re.match(r'(create\w+)\((.*)\)$', expr, re.S)
        if m and m.group(1) in create:
            params, body = create[m.group(1)]
            args = split_args(m.group(2))
            local = dict(subs)
            for p, a in zip(params, args):
                local[p] = a
            return parse_builder_items(body, local)
        return None

    trades = {}
    stats = {'direct': 0, 'create': 0, 'zip': 0, 'terracotta': 0}

    # direct register(context, CONST, EXPR)
    for m in re.finditer(r'(?<![\w.])register\(\s*context\s*,', src):
        args = split_args(balanced(src, src.find('(', m.start())))
        if len(args) >= 3 and args[1].strip() in single:
            trade = resolve_trade(args[2], {})
            if trade:
                trades[single[args[1].strip()]] = trade
                stats['create' if args[2].strip().startswith('create') else 'direct'] += 1

    # context.register(CONST, EXPR)
    for m in re.finditer(r'context\.register\(', src):
        args = split_args(balanced(src, src.find('(', m.start())))
        if len(args) >= 2 and args[0].strip() in single:
            trade = resolve_trade(args[1], {})
            if trade:
                trades[single[args[0].strip()]] = trade
                stats['direct'] += 1

    # ColorCollection.zipApply(PATH_COLL, ITEMS_EXPR, lambda)
    for m in re.finditer(r'ColorCollection\.zipApply\(', src):
        args = split_args(balanced(src, src.find('(', m.start())))
        if len(args) < 3:
            continue
        path_coll, items_expr, builder = args[0].strip(), args[1].strip(), args[2]
        suffix = ITEM_COLL_SUFFIX.get(items_expr)
        if path_coll not in coll or suffix is None:
            continue
        prefix, psuffix = coll[path_coll]
        for color in COLORS:
            item = 'minecraft:' + color + '_' + suffix
            trade = parse_builder_items(builder, {'item': item, 'wool': item, 'carpet': item,
                                                  'bed': item, 'banner': item, 'dye': item})
            if trade:
                trades[prefix + color + psuffix] = trade
                stats['zip'] += 1

    # registerWanderingTraderTerracottaSellTrades(context, CONST, ITEMS_EXPR)
    for m in re.finditer(r'registerWanderingTraderTerracottaSellTrades\(', src):
        args = split_args(balanced(src, src.find('(', m.start())))
        if len(args) < 3:
            continue
        path_coll, suffix = args[1].strip(), ITEM_COLL_SUFFIX.get(args[2].strip())
        if path_coll not in coll or suffix is None:
            continue
        prefix, psuffix = coll[path_coll]
        for color in COLORS:
            trades[prefix + color + psuffix] = (EMERALD, '', 'minecraft:' + color + '_' + suffix)
            stats['terracotta'] += 1

    # ---- profession mapping from tags ----
    common = []
    prof_tags = {}
    for prof in sorted(os.listdir(tags_dir)):
        pdir = os.path.join(tags_dir, prof)
        if not os.path.isdir(pdir) or prof == 'wandering_trader':
            continue
        values = []
        for fn in sorted(os.listdir(pdir)):
            with open(os.path.join(pdir, fn), encoding='utf-8') as f:
                values.extend(json.load(f).get('values', []))
        if prof == 'common_smith':
            common.extend(values)
        else:
            prof_tags.setdefault(prof, []).extend(values)

    def resolve_values(values):
        out = set()
        for v in values:
            if v.startswith('#'):
                out.update(common)
            else:
                out.add(v)
        return {(x.split(':', 1)[1] if ':' in x else x) for x in out if not x.startswith('#')}

    path_profs = {}
    for prof, values in prof_tags.items():
        for path in resolve_values(values):
            path_profs.setdefault(path, set()).add('minecraft:' + prof)

    # ---- catalog ----
    by_prof = {}
    for path, trade in trades.items():
        for prof in path_profs.get(path, ()):
            by_prof.setdefault(prof, {})[trade] = None

    out = []
    for prof in sorted(by_prof):
        cats, seen = [], set()
        for (cost1, cost2, result) in by_prof[prof]:
            cid = category_id(cost1, cost2, result)
            if cid in seen:
                continue
            seen.add(cid)
            cats.append({'id': cid, 'label': cid, 'cost1': [cost1],
                         'cost2': [cost2] if cost2 else [], 'result': [result]})
        out.append({'profession': prof, 'categories': cats})

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    unresolved = sorted(p for p in path_profs if p not in trades)
    print('trades=%d stats=%s professions=%d categories=%d unresolved=%d'
          % (len(trades), stats, len(out), sum(len(p['categories']) for p in out), len(unresolved)))
    for p in out:
        print('  %-28s %d' % (p['profession'], len(p['categories'])))
    for p in unresolved:
        print('  UNRESOLVED', p)


if __name__ == '__main__':
    main()

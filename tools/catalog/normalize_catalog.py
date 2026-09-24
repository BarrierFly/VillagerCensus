"""Collapse variant trade categories in the merged catalog.

The 1.21.11 generator collapses colour variants (banners, wool, ...) into one
category, while the 26.3 generator emits one category per colour. Merging the two
files by id therefore leaves both an aggregate and the per-colour categories.
This pass groups categories that describe the same logical trade and unions their
item lists, so the selection GUI shows one option per trade type.

Grouping key: profession + the set of cost item "kinds" (colour stripped) + the
set of result item "kinds". Map results collapse to the single kind "map" and
ignore the cost shape, so empty maps and explorer maps become one option.

Usage: normalize_catalog.py <input.json> [output.json]
"""

import json
import sys

COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
          'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']


def path(item_id):
    return item_id.split(':', 1)[1] if ':' in item_id else item_id


def strip_color(item_id):
    p = path(item_id)
    for c in COLORS:
        if p.startswith(c + '_'):
            return p[len(c) + 1:]
        if p.endswith('_' + c):
            return p[:-(len(c) + 1)]
    return p


def is_map(item_id):
    p = path(item_id)
    return p == 'map' or p == 'filled_map' or p.endswith('_map')


def item_kind(item_id):
    return 'map' if is_map(item_id) else strip_color(item_id)


def key_for(cat):
    result_kinds = tuple(sorted({item_kind(r) for r in cat['result']}))

    if result_kinds == ('map',):
        return ('map',)

    cost_kinds = tuple(sorted({strip_color(c) for c in (set(cat['cost1']) | set(cat['cost2']))}))
    return (cost_kinds, result_kinds)


def has_color(cid):
    return any(t in COLORS for t in cid.split('_'))


def canonical_id(ids):
    # Prefer the aggregate id: no colour token, then shortest, then lexicographic.
    return min(ids, key=lambda cid: (has_color(cid), len(cid), cid))


def main():
    in_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else in_path

    with open(in_path, encoding='utf-8') as f:
        data = json.load(f)

    out = []
    merged_away = 0

    for prof in data:
        groups = {}
        order = []
        for cat in prof['categories']:
            k = key_for(cat)
            if k not in groups:
                groups[k] = []
                order.append(k)
            groups[k].append(cat)

        cats = []
        for k in order:
            members = groups[k]
            merged_away += len(members) - 1

            cost1, cost2, result = set(), set(), set()
            for m in members:
                cost1.update(m['cost1'])
                cost2.update(m['cost2'])
                result.update(m['result'])

            if k == ('map',):
                # Wildcard the second cost so both the plain map (emerald only)
                # and the explorer maps (emerald + compass) match.
                cost2 = set()

            ids = sorted(m['id'] for m in members)
            cid = canonical_id(ids)
            aliases = [i for i in ids if i != cid]

            entry = {
                'id': cid,
                'label': cid,
                'cost1': sorted(cost1),
                'cost2': sorted(cost2),
                'result': sorted(result),
            }

            if aliases:
                entry['aliases'] = aliases

            cats.append(entry)

        cats.sort(key=lambda c: c['id'])
        out.append({'profession': prof['profession'], 'categories': cats})

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    total = sum(len(p['categories']) for p in out)
    print('professions=%d categories=%d merged_away=%d' % (len(out), total, merged_away))
    for p in out:
        print('  %-28s %d' % (p['profession'], len(p['categories'])))


if __name__ == '__main__':
    main()

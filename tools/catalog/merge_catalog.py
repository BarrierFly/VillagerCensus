import json
import sys

def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)

def main():
    out_path = sys.argv[1]
    inputs = sys.argv[2:]

    merged = {}
    for path in inputs:
        for prof in load(path):
            bucket = merged.setdefault(prof['profession'], {})
            for c in prof['categories']:
                e = bucket.setdefault(c['id'], {'cost1': set(), 'cost2': set(), 'result': set(),
                                                'enchanted': False})
                e['cost1'].update(c['cost1'])
                e['cost2'].update(c['cost2'])
                e['result'].update(c['result'])
                e['enchanted'] = e['enchanted'] or c.get('enchanted', False)

    out = []
    for prof in sorted(merged):
        cats = []
        for cid in sorted(merged[prof]):
            e = merged[prof][cid]
            cat = {
                'id': cid,
                'label': cid,
                'cost1': sorted(e['cost1']),
                'cost2': sorted(e['cost2']),
                'result': sorted(e['result']),
            }
            if e['enchanted']:
                cat['enchanted'] = True
            cats.append(cat)
        out.append({'profession': prof, 'categories': cats})

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    total = sum(len(p['categories']) for p in out)
    print('merged professions=%d categories=%d' % (len(out), total))
    for p in out:
        print('  %-28s %d' % (p['profession'], len(p['categories'])))

if __name__ == '__main__':
    main()

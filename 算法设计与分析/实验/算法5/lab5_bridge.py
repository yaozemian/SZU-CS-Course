from collections import deque
from time import perf_counter
import sys


def read_graph(filename):
    with open(filename, "r", encoding="utf-8") as f:
        n = int(f.readline())
        m = int(f.readline())
        edges = []
        for _ in range(m):
            u, v = map(int, f.readline().split())
            edges.append((u, v))
    return n, edges


def build_adj(n, edges):
    adj = [[] for _ in range(n)]
    for i, (u, v) in enumerate(edges):
        adj[u].append((v, i))
        adj[v].append((u, i))
    return adj


def reachable_without_edge(n, adj, source, target, removed_edge):
    visited = bytearray(n)
    stack = [source]
    visited[source] = 1
    while stack:
        u = stack.pop()
        if u == target:
            return True
        for v, edge_id in adj[u]:
            if edge_id == removed_edge or visited[v]:
                continue
            visited[v] = 1
            stack.append(v)
    return False


def bridges_baseline(n, edges):
    adj = build_adj(n, edges)
    bridges = []
    for i, (u, v) in enumerate(edges):
        if not reachable_without_edge(n, adj, u, v, i):
            bridges.append(i)
    return bridges


class DSU:
    def __init__(self, n):
        self.parent = list(range(n))
        self.rank = [0] * n

    def find(self, x):
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra == rb:
            return False
        if self.rank[ra] < self.rank[rb]:
            ra, rb = rb, ra
        self.parent[rb] = ra
        if self.rank[ra] == self.rank[rb]:
            self.rank[ra] += 1
        return True


class TreePathSkipper:
    def __init__(self, parent):
        self.parent = parent
        self.jump = list(range(len(parent)))

    def find(self, x):
        root = x
        while self.jump[root] != root:
            root = self.jump[root]
        while self.jump[x] != x:
            nxt = self.jump[x]
            self.jump[x] = root
            x = nxt
        return root

    def skip_to_parent(self, x):
        if self.parent[x] != -1:
            self.jump[x] = self.find(self.parent[x])


def bridges_fast(n, edges):
    dsu = DSU(n)
    tree = [[] for _ in range(n)]
    is_tree_edge = bytearray(len(edges))
    is_non_bridge = bytearray(len(edges))
    non_tree_edges = []

    for i, (u, v) in enumerate(edges):
        if dsu.union(u, v):
            is_tree_edge[i] = 1
            tree[u].append((v, i))
            tree[v].append((u, i))
        else:
            non_tree_edges.append(i)
            is_non_bridge[i] = 1

    parent = [-1] * n
    depth = [0] * n
    parent_edge = [-1] * n
    visited = bytearray(n)

    for root in range(n):
        if visited[root]:
            continue
        visited[root] = 1
        q = deque([root])
        while q:
            u = q.popleft()
            for v, edge_id in tree[u]:
                if visited[v]:
                    continue
                visited[v] = 1
                parent[v] = u
                depth[v] = depth[u] + 1
                parent_edge[v] = edge_id
                q.append(v)

    skipper = TreePathSkipper(parent)

    for edge_id in non_tree_edges:
        u, v = edges[edge_id]
        a, b = skipper.find(u), skipper.find(v)
        while a != b:
            if depth[a] < depth[b]:
                a, b = b, a
            pe = parent_edge[a]
            if pe == -1:
                break
            is_non_bridge[pe] = 1
            skipper.skip_to_parent(a)
            a, b = skipper.find(a), skipper.find(b)

    return [i for i in range(len(edges)) if is_tree_edge[i] and not is_non_bridge[i]]


def sample_graph2():
    return 16, [
        (0, 1), (0, 2), (1, 2),
        (2, 3),
        (3, 4), (3, 5), (4, 5),
        (5, 6),
        (6, 7), (7, 8), (8, 9), (9, 6),
        (9, 10),
        (10, 11), (10, 12), (11, 12),
        (12, 13),
        (13, 14),
        (14, 15),
    ]


def run(name, n, edges, algorithm):
    start = perf_counter()
    bridges = algorithm(n, edges)
    seconds = perf_counter() - start
    shown = " ".join(f"({edges[i][0]},{edges[i][1]})" for i in bridges[:20])
    if len(bridges) > 20:
        shown += " ..."
    print(name)
    print(f"vertices: {n}, edges: {len(edges)}")
    print(f"bridge count: {len(bridges)}")
    print(f"time: {seconds:.6f} s")
    print(f"bridges: {shown}\n")
    return bridges, seconds


def main():
    if len(sys.argv) == 1:
        n, edges = sample_graph2()
        base, _ = run("sample graph2 - baseline", n, edges, bridges_baseline)
        fast, _ = run("sample graph2 - fast dsu", n, edges, bridges_fast)
        print("same result:", "yes" if base == fast else "no")
        return

    filename = sys.argv[1]
    mode = sys.argv[2] if len(sys.argv) >= 3 else "fast"
    n, edges = read_graph(filename)
    if mode == "baseline":
        run(filename + " - baseline", n, edges, bridges_baseline)
    elif mode == "both":
        base, _ = run(filename + " - baseline", n, edges, bridges_baseline)
        fast, _ = run(filename + " - fast dsu", n, edges, bridges_fast)
        print("same result:", "yes" if base == fast else "no")
    else:
        run(filename + " - fast dsu", n, edges, bridges_fast)


if __name__ == "__main__":
    main()

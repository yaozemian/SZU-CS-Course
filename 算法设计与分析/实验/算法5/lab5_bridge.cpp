#include <algorithm>
#include <chrono>
#include <fstream>
#include <iostream>
#include <numeric>
#include <queue>
#include <string>
#include <tuple>
#include <vector>

using namespace std;

struct Edge {
    int u;
    int v;
};

class DSU {
public:
    explicit DSU(int n = 0) { reset(n); }

    void reset(int n) {
        parent.resize(n);
        rank.assign(n, 0);
        iota(parent.begin(), parent.end(), 0);
    }

    int find(int x) {
        if (parent[x] == x) return x;
        parent[x] = find(parent[x]);
        return parent[x];
    }

    bool unite(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return false;
        if (rank[ra] < rank[rb]) swap(ra, rb);
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) rank[ra]++;
        return true;
    }

private:
    vector<int> parent;
    vector<int> rank;
};

struct Graph {
    int n = 0;
    vector<Edge> edges;
};

Graph readGraph(const string& filename) {
    ifstream in(filename);
    if (!in) {
        throw runtime_error("cannot open file: " + filename);
    }

    Graph g;
    int m;
    in >> g.n >> m;
    g.edges.reserve(m);
    for (int i = 0; i < m; ++i) {
        int u, v;
        in >> u >> v;
        g.edges.push_back({u, v});
    }
    return g;
}

vector<vector<pair<int, int>>> buildAdjacency(const Graph& g) {
    vector<vector<pair<int, int>>> adj(g.n);
    for (int i = 0; i < (int)g.edges.size(); ++i) {
        auto [u, v] = g.edges[i];
        adj[u].push_back({v, i});
        adj[v].push_back({u, i});
    }
    return adj;
}

bool reachableWithoutEdge(
    int n,
    const vector<vector<pair<int, int>>>& adj,
    int source,
    int target,
    int removedEdge
) {
    vector<char> visited(n, false);
    vector<int> stack;
    stack.push_back(source);
    visited[source] = true;

    while (!stack.empty()) {
        int u = stack.back();
        stack.pop_back();
        if (u == target) return true;

        for (auto [v, edgeId] : adj[u]) {
            if (edgeId == removedEdge || visited[v]) continue;
            visited[v] = true;
            stack.push_back(v);
        }
    }
    return false;
}

vector<int> findBridgesBaseline(const Graph& g) {
    auto adj = buildAdjacency(g);
    vector<int> bridges;

    for (int i = 0; i < (int)g.edges.size(); ++i) {
        auto [u, v] = g.edges[i];
        if (!reachableWithoutEdge(g.n, adj, u, v, i)) {
            bridges.push_back(i);
        }
    }
    return bridges;
}

class TreePathSkipper {
public:
    explicit TreePathSkipper(const vector<int>& treeParent) : parent(treeParent) {
        jump.resize(parent.size());
        iota(jump.begin(), jump.end(), 0);
    }

    int find(int x) {
        if (jump[x] == x) return x;
        jump[x] = find(jump[x]);
        return jump[x];
    }

    void skipToParent(int x) {
        if (parent[x] != -1) {
            jump[x] = find(parent[x]);
        }
    }

private:
    const vector<int>& parent;
    vector<int> jump;
};

vector<int> findBridgesFast(const Graph& g) {
    DSU component(g.n);
    vector<vector<pair<int, int>>> tree(g.n);
    vector<int> nonTreeEdges;
    vector<char> isTreeEdge(g.edges.size(), false);
    vector<char> isNonBridge(g.edges.size(), false);

    for (int i = 0; i < (int)g.edges.size(); ++i) {
        auto [u, v] = g.edges[i];
        if (component.unite(u, v)) {
            isTreeEdge[i] = true;
            tree[u].push_back({v, i});
            tree[v].push_back({u, i});
        } else {
            nonTreeEdges.push_back(i);
            isNonBridge[i] = true;
        }
    }

    vector<int> parent(g.n, -1), depth(g.n, 0), parentEdge(g.n, -1);
    vector<char> visited(g.n, false);

    for (int root = 0; root < g.n; ++root) {
        if (visited[root]) continue;
        queue<int> q;
        q.push(root);
        visited[root] = true;

        while (!q.empty()) {
            int u = q.front();
            q.pop();
            for (auto [v, edgeId] : tree[u]) {
                if (visited[v]) continue;
                visited[v] = true;
                parent[v] = u;
                depth[v] = depth[u] + 1;
                parentEdge[v] = edgeId;
                q.push(v);
            }
        }
    }

    TreePathSkipper skipper(parent);

    auto markPathAsNonBridge = [&](int u, int v) {
        int a = skipper.find(u);
        int b = skipper.find(v);
        while (a != b) {
            if (depth[a] < depth[b]) swap(a, b);
            int edgeId = parentEdge[a];
            if (edgeId == -1) break;
            isNonBridge[edgeId] = true;
            skipper.skipToParent(a);
            a = skipper.find(a);
            b = skipper.find(b);
        }
    };

    for (int edgeId : nonTreeEdges) {
        auto [u, v] = g.edges[edgeId];
        markPathAsNonBridge(u, v);
    }

    vector<int> bridges;
    for (int i = 0; i < (int)g.edges.size(); ++i) {
        if (isTreeEdge[i] && !isNonBridge[i]) {
            bridges.push_back(i);
        }
    }
    return bridges;
}

Graph sampleGraph2() {
    Graph g;
    g.n = 16;
    g.edges = {
        {0, 1}, {0, 2}, {1, 2},
        {2, 3},
        {3, 4}, {3, 5}, {4, 5},
        {5, 6},
        {6, 7}, {7, 8}, {8, 9}, {9, 6},
        {9, 10},
        {10, 11}, {10, 12}, {11, 12},
        {12, 13},
        {13, 14},
        {14, 15}
    };
    return g;
}

template <typename Func>
pair<vector<int>, double> timed(Func&& func) {
    auto start = chrono::steady_clock::now();
    vector<int> result = func();
    auto end = chrono::steady_clock::now();
    chrono::duration<double> elapsed = end - start;
    return {result, elapsed.count()};
}

void printResult(const string& name, const Graph& g, const vector<int>& bridges, double seconds) {
    cout << name << "\n";
    cout << "vertices: " << g.n << ", edges: " << g.edges.size() << "\n";
    cout << "bridge count: " << bridges.size() << "\n";
    cout << "time: " << seconds << " s\n";
    cout << "bridges:";
    int limit = min<int>(bridges.size(), 20);
    for (int i = 0; i < limit; ++i) {
        const auto& e = g.edges[bridges[i]];
        cout << " (" << e.u << "," << e.v << ")";
    }
    if ((int)bridges.size() > limit) cout << " ...";
    cout << "\n\n";
}

int main(int argc, char* argv[]) {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    try {
        if (argc < 2) {
            Graph g = sampleGraph2();
            auto [baseBridges, baseTime] = timed([&] { return findBridgesBaseline(g); });
            auto [fastBridges, fastTime] = timed([&] { return findBridgesFast(g); });
            printResult("sample graph2 - baseline", g, baseBridges, baseTime);
            printResult("sample graph2 - fast dsu", g, fastBridges, fastTime);
            return 0;
        }

        string filename = argv[1];
        string mode = argc >= 3 ? argv[2] : "fast";
        Graph g = readGraph(filename);

        if (mode == "baseline") {
            auto [bridges, seconds] = timed([&] { return findBridgesBaseline(g); });
            printResult(filename + " - baseline", g, bridges, seconds);
        } else if (mode == "both") {
            auto [baseBridges, baseTime] = timed([&] { return findBridgesBaseline(g); });
            auto [fastBridges, fastTime] = timed([&] { return findBridgesFast(g); });
            printResult(filename + " - baseline", g, baseBridges, baseTime);
            printResult(filename + " - fast dsu", g, fastBridges, fastTime);
            cout << "same result: " << (baseBridges == fastBridges ? "yes" : "no") << "\n";
        } else {
            auto [bridges, seconds] = timed([&] { return findBridgesFast(g); });
            printResult(filename + " - fast dsu", g, bridges, seconds);
        }
    } catch (const exception& ex) {
        cerr << "error: " << ex.what() << "\n";
        return 1;
    }

    return 0;
}

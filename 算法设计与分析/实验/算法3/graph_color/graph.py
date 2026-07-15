import time
import sys
import random
import os
import matplotlib.pyplot as plt

sys.setrecursionlimit(1000000)

plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False


class GraphColoring:
    def __init__(self, vertex_count, edge_count, color_count, edges,
                 first_vertex=None, use_mrv=True, use_forward=True,
                 use_dh=True, use_layer_dedup=True, time_limit=5):
        self.V = vertex_count
        self.E = edge_count
        self.COLOR = color_count
        self.first_vertex = first_vertex

        self.use_mrv = use_mrv
        self.use_forward = use_forward
        self.use_dh = use_dh
        self.use_layer_dedup = use_layer_dedup
        self.time_limit = time_limit
        self.timeout = False

        temp_adj = [[] for _ in range(vertex_count + 1)]
        for u, v in edges:
            temp_adj[u].append(v)
            temp_adj[v].append(u)

        self.adj = [tuple(neighbors) for neighbors in temp_adj]
        self.degree = [len(n) for n in temp_adj]

        self.v_color = [0] * (vertex_count + 1)
        self.state = [[1] * (color_count + 1) for _ in range(vertex_count + 1)]
        self.choice = [color_count] * (vertex_count + 1)

        self.sum = 0
        self.first_solution_time = None
        self.found_first = False
        self.start_time = 0
        self.max_color_used = 0
        self.find_first_only = False

    def reset(self):
        self.sum = 0
        self.found_first = False
        self.first_solution_time = None
        self.max_color_used = 0
        self.timeout = False

        for v in range(1, self.V + 1):
            self.v_color[v] = 0
            for c in range(1, self.COLOR + 1):
                self.state[v][c] = 1
            self.choice[v] = self.COLOR

    def solve(self, stop_at_1e9=True, find_first_only=False):
        self.reset()
        self.find_first_only = find_first_only
        self.start_time = time.time()

        if self.first_vertex is not None:
            first = self.first_vertex
        else:
            first = max(range(1, self.V + 1), key=lambda i: self.degree[i])

        self._dfs(first, stop_at_1e9)

        elapsed = time.time() - self.start_time
        if self.first_solution_time is None:
            self.first_solution_time = elapsed

        return elapsed, self.sum, self.first_solution_time, self.timeout

    def _get_next(self):
        best_v = 0

        if self.use_mrv:
            min_choice = self.COLOR + 1
            for v in range(1, self.V + 1):
                if self.v_color[v] == 0:
                    ch = self.choice[v]
                    if ch < min_choice:
                        min_choice = ch
                        best_v = v
                    elif ch == min_choice and self.use_dh and self.degree[v] > self.degree[best_v]:
                        best_v = v
        else:
            for v in range(1, self.V + 1):
                if self.v_color[v] == 0:
                    best_v = v
                    break

        return best_v

    def _dfs(self, current, stop_at_1e9):
        if time.time() - self.start_time > self.time_limit:
            self.timeout = True
            return

        if stop_at_1e9 and self.sum > 1000000000:
            return

        self.v_color[current] = -1
        ancestor_max = self.max_color_used
        first_new_sol = 0
        found_first_new = False

        state_cur = self.state[current]
        adj_cur = self.adj[current]

        for i in range(1, self.COLOR + 1):
            if self.timeout:
                self.v_color[current] = 0
                return

            if state_cur[i] != 1:
                continue

            conflict = False
            for j in adj_cur:
                if self.v_color[j] == i:
                    conflict = True
                    break

            if conflict:
                continue

            self.v_color[current] = i

            is_new = i > self.max_color_used
            if is_new:
                self.max_color_used = i

            changes = []
            ok = True

            if self.use_forward:
                for j in adj_cur:
                    if self.v_color[j] == 0 and self.state[j][i] == 1:
                        self.state[j][i] = -current
                        self.choice[j] -= 1
                        changes.append(j)
                        if self.choice[j] == 0:
                            ok = False
                            break

            if ok:
                next_v = self._get_next()

                if next_v == 0:
                    if not self.found_first:
                        self.found_first = True
                        self.first_solution_time = time.time() - self.start_time

                    self.sum += 1

                    if self.find_first_only:
                        self._undo_check(current, i, changes)
                        self.v_color[current] = 0
                        self.max_color_used = ancestor_max
                        return

                    if is_new and not found_first_new:
                        first_new_sol = 1
                        found_first_new = True

                else:
                    sum_before = self.sum
                    self._dfs(next_v, stop_at_1e9)
                    branch_sol = self.sum - sum_before

                    if self.find_first_only and self.found_first:
                        self._undo_check(current, i, changes)
                        self.v_color[current] = 0
                        self.max_color_used = ancestor_max
                        return

                    if is_new and not found_first_new:
                        first_new_sol = branch_sol
                        found_first_new = True

            self._undo_check(current, i, changes)

            self.v_color[current] = 0
            if is_new:
                self.max_color_used = ancestor_max

            if self.use_layer_dedup and is_new and found_first_new:
                rem = self.COLOR - i
                if rem > 0:
                    self.sum += first_new_sol * rem
                break

        self.max_color_used = ancestor_max
        self.v_color[current] = 0

    def _undo_check(self, current, i, changes):
        for j in reversed(changes):
            self.choice[j] += 1
            if self.state[j][i] == -current:
                self.state[j][i] = 1


def read_col_file(filepath):
    edges = []
    vertex_count = 0

    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('c'):
                continue
            if line.startswith('p'):
                vertex_count = int(line.split()[2])
            elif line.startswith('e'):
                parts = line.split()
                edges.append((int(parts[1]), int(parts[2])))

    return vertex_count, len(edges), edges


def generate_random_graph(vertex_count, edge_count):
    edges = set()

    for v in range(2, vertex_count + 1):
        edges.add((random.randint(1, v - 1), v))

    max_possible = vertex_count * (vertex_count - 1) // 2
    edge_count = min(edge_count, max_possible)
    edge_count = max(edge_count, vertex_count - 1)

    all_pairs = [
        (u, v)
        for u in range(1, vertex_count + 1)
        for v in range(u + 1, vertex_count + 1)
        if (u, v) not in edges
    ]

    random.shuffle(all_pairs)

    needed = edge_count - len(edges)
    if needed > 0:
        for pair in all_pairs[:needed]:
            edges.add(pair)

    return list(edges)


def small_map_test():
    print("\n" + "=" * 60)
    print("一、小地图四色测试")
    print("=" * 60)

    small_edges = [
        (1, 2), (1, 3), (1, 4),
        (2, 3), (2, 4), (2, 5),
        (3, 4),
        (4, 5), (4, 6), (4, 7),
        (5, 6), (5, 8),
        (6, 7), (6, 8), (6, 9),
        (7, 9),
        (8, 9)
    ]

    gc = GraphColoring(9, len(small_edges), 4, small_edges, time_limit=5)
    elapsed, solutions, first_time, timeout = gc.solve(stop_at_1e9=False)

    print(f"顶点数: 9")
    print(f"边数: {len(small_edges)}")
    print(f"颜色数: 4")
    print(f"运行时间: {elapsed * 1000:.3f} ms")
    print(f"解的个数: {solutions}")
    print(f"第一个可行解时间: {first_time * 1000:.3f} ms")
    print(f"是否超时: {timeout}")


def leighton_test():
    print("\n" + "=" * 60)
    print("二、Leighton 图着色测试")
    print("=" * 60)

    test_cases = [
        ("le450_5a.col", 5, None, False),
        ("le450_15b.col", 15, 4, True),
        ("le450_25a.col", 25, None, True)
    ]

    results = []

    for filename, color_count, first_vertex, first_only in test_cases:
        # 直接使用文件名，读取同目录下的文件
        filepath = filename

        if not os.path.exists(filepath):
            print(f"{filepath} 不存在，跳过")
            continue

        vertex_count, edge_count, edges = read_col_file(filepath)

        gc = GraphColoring(
            vertex_count,
            edge_count,
            color_count,
            edges,
            first_vertex=first_vertex,
            time_limit=10
        )

        elapsed, solutions, first_time, timeout = gc.solve(
            stop_at_1e9=True,
            find_first_only=first_only
        )

        print(f"\n图文件: {filename}")
        print(f"顶点数: {vertex_count}, 边数: {edge_count}, 颜色数: {color_count}")
        print(f"运行时间: {elapsed:.3f}s")
        print(f"解的个数: {solutions}")
        print(f"第一个可行解时间: {first_time:.3f}s")
        print(f"是否超时: {timeout}")

        results.append((filename, elapsed, first_time, solutions))

    if results:
        labels = [r[0] for r in results]
        times = [r[1] for r in results]

        plt.figure(figsize=(8, 5))
        plt.bar(labels, times)
        plt.xlabel("图文件")
        plt.ylabel("运行时间 / 秒")
        plt.title("Leighton 图着色测试结果")
        plt.tight_layout()
        plt.savefig("leighton_test.png", dpi=200)
        plt.show()


def pruning_strategy_test():
    print("\n" + "=" * 60)
    print("三、剪枝策略效率对比")
    print("=" * 60)

    # 增大图的规模，让差异更明显
    vertex_count = 100
    edge_count = 200
    color_count = 4
    edges = generate_random_graph(vertex_count, edge_count)

    strategies = [
        ("无优化", False, False, False, False),
        ("Forward Checking", False, True, False, False),
        ("MRV + DH", True, False, True, False),
        ("综合优化", True, True, True, True)
    ]

    names = []
    times = []
    search_nodes_list = []

    for name, use_mrv, use_forward, use_dh, use_layer_dedup in strategies:
        gc = GraphColoring(
            vertex_count,
            edge_count,
            color_count,
            edges,
            use_mrv=use_mrv,
            use_forward=use_forward,
            use_dh=use_dh,
            use_layer_dedup=use_layer_dedup,
            time_limit=10
        )

        # 统计搜索节点数而非时间，更能反映策略效率
        # 找到第一个解就停止
        elapsed, solutions, first_time, timeout = gc.solve(
            stop_at_1e9=True,
            find_first_only=True
        )

        print(f"{name}: 时间={elapsed:.4f}s, 搜索节点数={gc.sum}, 是否超时={timeout}")

        names.append(name)
        times.append(elapsed)
        search_nodes_list.append(gc.sum)

    # 绘制时间对比图
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

    ax1.bar(names, times, color=['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4'])
    ax1.set_xlabel("剪枝策略", fontsize=12)
    ax1.set_ylabel("运行时间 / 秒", fontsize=12)
    ax1.set_title("不同剪枝策略运行时间对比\n(100点200边4色，找第一个解)", fontsize=13)
    ax1.grid(axis='y', alpha=0.3)
    for i, (bar, t) in enumerate(zip(ax1.patches, times)):
        ax1.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.01,
                '{:.4f}s'.format(t), ha='center', va='bottom', fontsize=10)

    # 绘制搜索节点数对比图（更能看出差异）
    ax2.bar(names, search_nodes_list, color=['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4'])
    ax2.set_xlabel("剪枝策略", fontsize=12)
    ax2.set_ylabel("搜索节点数", fontsize=12)
    ax2.set_title("不同剪枝策略搜索节点数对比\n(节点数越少说明剪枝效果越好)", fontsize=13)
    ax2.grid(axis='y', alpha=0.3)
    ax2.set_yscale('log')  # 对数坐标，让差异更明显
    for i, (bar, nodes) in enumerate(zip(ax2.patches, search_nodes_list)):
        ax2.text(bar.get_x() + bar.get_width() / 2, bar.get_height() * 1.1,
                '{:.0f}'.format(nodes), ha='center', va='bottom', fontsize=9, rotation=45)

    plt.tight_layout()
    plt.savefig("剪枝策略对比.png", dpi=200)
    print("\n图表已保存至 剪枝策略对比.png")
    plt.show()


def average_time(vertex_count, edge_count, color_count, repeat=3, time_limit=5):
    total_time = 0

    for _ in range(repeat):
        edges = generate_random_graph(vertex_count, edge_count)
        gc = GraphColoring(
            vertex_count,
            edge_count,
            color_count,
            edges,
            time_limit=time_limit
        )

        elapsed, solutions, first_time, timeout = gc.solve(
            stop_at_1e9=True,
            find_first_only=True  # 找到第一个解就停止
        )
        total_time += elapsed

    return total_time / repeat


def experiment_vertex_change():
    print("\n" + "=" * 60)
    print("四-1、点数变化：边数 = 点数 × 2，颜色数固定")
    print("=" * 60)

    color_count = 4
    vertex_list = [20, 30, 40, 50, 60, 70, 80]
    times = []

    for n in vertex_list:
        m = 2 * n
        avg = average_time(n, m, color_count, repeat=3, time_limit=5)
        times.append(avg)
        print(f"点数={n}, 边数={m}, 颜色数={color_count}, 平均时间={avg:.3f}s")

    plt.figure(figsize=(8, 5))
    plt.plot(vertex_list, times, marker='o')
    plt.xlabel("顶点数")
    plt.ylabel("平均运行时间 / 秒")
    plt.title("图规模对算法效率的影响\n边数 = 顶点数 × 2，颜色数 = 4")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("图1_点数变化.png", dpi=200)
    plt.show()


def experiment_edge_change():
    print("\n" + "=" * 60)
    print("四-2、边数变化：点数固定，颜色数固定")
    print("=" * 60)

    vertex_count = 80
    color_count = 4
    edge_list = [80, 120, 160, 200, 240, 280]
    times = []

    for m in edge_list:
        avg = average_time(vertex_count, m, color_count, repeat=3, time_limit=5)
        times.append(avg)
        print(f"点数={vertex_count}, 边数={m}, 颜色数={color_count}, 平均时间={avg:.3f}s")

    plt.figure(figsize=(8, 5))
    plt.plot(edge_list, times, marker='s')
    plt.xlabel("边数")
    plt.ylabel("平均运行时间 / 秒")
    plt.title("边数对算法效率的影响\n顶点数 = 80，颜色数 = 4")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("图2_边数变化.png", dpi=200)
    plt.show()


def experiment_color_change():
    print("\n" + "=" * 60)
    print("四-3、颜色数变化：点数和边数固定")
    print("=" * 60)

    vertex_count = 80
    edge_count = 160
    color_list = [3, 4, 5, 6, 7, 8]
    times = []

    edges = generate_random_graph(vertex_count, edge_count)

    for c in color_list:
        total = 0

        for _ in range(3):
            gc = GraphColoring(
                vertex_count,
                edge_count,
                c,
                edges,
                time_limit=5
            )

            elapsed, solutions, first_time, timeout = gc.solve(
                stop_at_1e9=True,
                find_first_only=True  # 找到第一个解就停止
            )
            total += elapsed

        avg = total / 3
        times.append(avg)

        print(f"点数={vertex_count}, 边数={edge_count}, 颜色数={c}, 平均时间={avg:.3f}s")

    plt.figure(figsize=(8, 5))
    plt.plot(color_list, times, marker='^')
    plt.xlabel("颜色数")
    plt.ylabel("平均运行时间 / 秒")
    plt.title("颜色数对算法效率的影响\n顶点数 = 80，边数 = 160")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("图3_颜色数变化.png", dpi=200)
    plt.show()


def main():
    random.seed(0)

    # small_map_test()
    # leighton_test()
    pruning_strategy_test()
    #
    # experiment_vertex_change()
    # experiment_edge_change()
    # experiment_color_change()
    #
    # print("\n实验全部完成，已生成图片：")
    # print("leighton_test.png")
    # print("pruning_strategy.png")
    # print("图1_点数变化.png")
    # print("图2_边数变化.png")
    # print("图3_颜色数变化.png")


if __name__ == "__main__":
    main()
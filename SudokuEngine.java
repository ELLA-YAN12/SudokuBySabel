package sudokuBySabel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 数独の基本ルール判定、盤面の検証、自動解法（ソルバー）、解の個数カウント、
 * および問題の自動生成機能を提供するユーティリティクラス。
 *
 * 【盤面フォーマット】
 * ・盤面は int[9][9] の2次元配列で表されます。
 * ・board[row][col] で各マスにアクセスします（インデックスは 0〜8）。
 * ・数値 0 は空欄を表し、1〜9 は数字が配置されていることを表します。
 */
public final class SudokuEngine {

    /**
     * ユーティリティクラスのためインスタンス化を防止します。
     * すべてのメソッドは static なので、SudokuEngine.canPlace(...) のように呼び出します。
     */
    private SudokuEngine() {
    }

    /**
     * 生成された問題盤面（ヒント）と完成した解答盤面を保持するデータ構造クラス。
     */
    public static final class Puzzle {

        /** 初期問題盤面（0 は空欄） */
        public final int[][] clues;
        /** 完成した解答盤面 */
        public final int[][] solution;

        /**
         * Puzzle オブジェクトを構築します。配列の独立性を保つためディープコピーを行います。
         *
         * @param clues    初期問題盤面
         * @param solution 解答盤面
         */
        private Puzzle(int[][] clues, int[][] solution) {
            this.clues = copy(clues);
            this.solution = copy(solution);
        }
    }

    /**
     * 配列が 9行9列 の正当な構造を持っているか確認します。
     *
     * @param board 確認対象の盤面配列
     * @return 9行9列であれば true、null やサイズ不正の場合は false
     */
    private static boolean hasValidSize(int[][] board) {
        if (board == null || board.length != 9) {
            return false;
        }

        for (int row = 0; row < 9; row++) {
            if (board[row] == null || board[row].length != 9) {
                return false;
            }
        }

        return true;
    }

    /**
     * 盤面配列の独立したディープコピー（複製）を作成します。
     *
     * @param board コピー元の盤面
     * @return 同じ数値を持つ新しい 9x9 盤面配列
     * @throws IllegalArgumentException 盤面サイズが 9x9 でない場合
     */
    public static int[][] copy(int[][] board) {
        if (!hasValidSize(board)) {
            throw new IllegalArgumentException(
                    "盤面は必ず 9行9列 である必要があります。"
            );
        }

        int[][] result = new int[9][9];

        for (int row = 0; row < 9; row++) {
            result[row] = board[row].clone();
        }

        return result;
    }

    /**
     * 指定したマスに特定の数値を配置しても数独のルールに違反しないか検証します。
     * 行・列・3x3ブロック内で数値が重複していないかをチェックします。
     *
     * @param board 現在の盤面
     * @param row   対象の行インデックス (0〜8)
     * @param col   対象の列インデックス (0〜8)
     * @param value 配置したい数値 (1〜9)
     * @return ルール違反がなければ true、重複があれば false
     */
    public static boolean canPlace(
            int[][] board, int row, int col, int value) {

        if (!hasValidSize(board)) {
            return false;
        }

        if (row < 0 || row >= 9 || col < 0 || col >= 9) {
            return false;
        }

        if (value < 1 || value > 9) {
            return false;
        }

        // 行および列の重複をチェック（自分自身のマスは除外）
        for (int i = 0; i < 9; i++) {

            if (i != col && board[row][i] == value) {
                return false;
            }

            if (i != row && board[i][col] == value) {
                return false;
            }
        }

        // 対象のマスが含まれる 3x3 ブロックの左上座標を計算
        int boxRow = row / 3 * 3;
        int boxCol = col / 3 * 3;

        // 3x3 ブロック内の重複をチェック
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                boolean isTargetSquare = r == row && c == col;

                if (!isTargetSquare && board[r][c] == value) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 盤面全体が現在ルール違反を起こしていないか検証します（空欄は許容されます）。
     *
     * @param board 検証対象の盤面
     * @return 矛盾がなければ true、数値の重複や不正値があれば false
     */
    public static boolean isValid(int[][] board) {
        if (!hasValidSize(board)) {
            return false;
        }

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int value = board[row][col];

                if (value == 0) {
                    continue;
                }

                if (!canPlace(board, row, col, value)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 盤面のすべてのマスがルール通り正しく埋まっている（完成している）か判定します。
     *
     * @param board 検証対象の盤面
     * @return 完成していれば true、空欄や矛盾があれば false
     */
    public static boolean isComplete(int[][] board) {
        if (!hasValidSize(board)) {
            return false;
        }

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (!canPlace(board, row, col, board[row][col])) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 元の盤面を変更せずに自動解法（ソルバー）を実行し、完成した解答盤面を返します。
     *
     * @param board 解答を求める問題盤面
     * @return 解答盤面（解が存在しない、または無効な問題の場合は null）
     */
    public static int[][] solve(int[][] board) {
        if (!isValid(board)) {
            return null;
        }

        int[][] answer = copy(board);

        if (solveRecursive(answer)) {
            return answer;
        }

        return null;
    }

    /**
     * バックトラッキング（試行錯誤）を用いて再帰的に数独を解く内部ヘルパーメソッド。
     *
     * @param board 探索を行う作業用盤面
     * @return 解が見つかった場合は true
     */
    private static boolean solveRecursive(int[][] board) {
        int[] emptyCell = findBestEmptyCell(board);

        // 空欄が存在しない場合は解法完了
        if (emptyCell == null) {
            return true;
        }

        int row = emptyCell[0];
        int col = emptyCell[1];

        for (int value = 1; value <= 9; value++) {
            if (!canPlace(board, row, col, value)) {
                continue;
            }

            board[row][col] = value;

            if (solveRecursive(board)) {
                return true;
            }

            // 失敗した場合は状態を元に戻す（バックトラッキング）
            board[row][col] = 0;
        }

        return false;
    }

    /**
     * 最小残り候補値（MRV）ヒューリスティックに基づき、入れることのできる数字の選択肢が
     * 最も少ない空欄マスを探索します。
     *
     * @param board 探索対象の盤面
     * @return {row, col} の配列（空欄がない場合は null）
     */
    private static int[] findBestEmptyCell(int[][] board) {
        int[] bestCell = null;
        int fewestOptions = 10;

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (board[row][col] != 0) {
                    continue;
                }

                int options = 0;

                for (int value = 1; value <= 9; value++) {
                    if (canPlace(board, row, col, value)) {
                        options++;
                    }
                }

                if (options < fewestOptions) {
                    fewestOptions = options;
                    bestCell = new int[] {row, col};

                    // 候補数が0のマスがあれば探索を即座に中断（詰み状態）
                    if (options == 0) {
                        return bestCell;
                    }
                }
            }
        }

        return bestCell;
    }

    /**
     * 盤面の解の個数を指定した上限（limit）までカウントします。
     * （例: limit=2 の場合、一意解か複数解かを高速に判定できます）
     *
     * @param board カウント対象の盤面
     * @param limit 検索する上限数
     * @return 発見された解の個数（limit で打ち切り）
     */
    public static int countSolutions(int[][] board, int limit) {
        if (limit <= 0 || !isValid(board)) {
            return 0;
        }

        return count(copy(board), limit);
    }

    /**
     * 解の個数をカウントする再帰ヘルパーメソッド。
     *
     * @param board 作業用盤面
     * @param limit 残りのカウント上限
     * @return 発見した解の個数
     */
    private static int count(int[][] board, int limit) {
        if (limit <= 0) {
            return 0;
        }

        int[] emptyCell = findBestEmptyCell(board);

        if (emptyCell == null) {
            return 1;
        }

        int row = emptyCell[0];
        int col = emptyCell[1];
        int total = 0;

        for (int value = 1; value <= 9 && total < limit; value++) {
            if (!canPlace(board, row, col, value)) {
                continue;
            }

            board[row][col] = value;

            // 必要な数だけ追加でカウント
            total += count(board, limit - total);

            // 次の数値を試すため状態を復元
            board[row][col] = 0;
        }

        return total;
    }

    /**
     * 解が必ず「唯一1つ（一意解）」存在するランダムな数独問題を生成します。
     *
     * @param targetClues 残したい目標ヒント数 (0〜81)
     * @param random      乱数生成器インスタンス
     * @return 生成された Puzzle インスタンス（問題と解答ペア）
     */
    public static Puzzle generate(int targetClues, Random random) {
        if (targetClues < 0 || targetClues > 81) {
            throw new IllegalArgumentException(
                    "targetClues は 0〜81 の範囲で指定してください。"
            );
        }

        if (random == null) {
            throw new IllegalArgumentException(
                    "Random インスタンスが必要です。"
            );
        }

        // 3x3ブロック構造を壊さないように行と列をシャッフル
        int[] rows = shuffledGroups(random);
        int[] cols = shuffledGroups(random);

        List<Integer> digits = new ArrayList<>();

        for (int value = 1; value <= 9; value++) {
            digits.add(value);
        }

        Collections.shuffle(digits, random);

        int[][] solution = new int[9][9];

        // 規則的なパターンを用いて完全な正解盤面を作成
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = (
                        rows[row] * 3
                        + rows[row] / 3
                        + cols[col]
                ) % 9;

                solution[row][col] = digits.get(index);
            }
        }

        int[][] clues = copy(solution);
        List<Integer> cells = new ArrayList<>();

        for (int i = 0; i < 81; i++) {
            cells.add(i);
        }

        Collections.shuffle(cells, random);

        int remaining = 81;

        // 解が唯一1つに保たれることを確認しながら、順番に数字を削っていく
        for (int cell : cells) {
            if (remaining <= targetClues) {
                break;
            }

            int row = cell / 9;
            int col = cell % 9;
            int oldValue = clues[row][col];

            clues[row][col] = 0;

            if (countSolutions(clues, 2) == 1) {
                // 解が1つに定まる場合は削った状態を維持
                remaining--;
            } else {
                // 解が複数できてしまう場合は数字を元に戻す
                clues[row][col] = oldValue;
            }
        }

        return new Puzzle(clues, solution);
    }

    /**
     * 3x3ブロック構造の妥当性を維持したまま、行や列のインデックス（0〜8）をランダムに並べ替えます。
     *
     * @param random 乱数生成器
     * @return ランダムに変換された 9要素のインデックス配列
     */
    private static int[] shuffledGroups(Random random) {
        List<Integer> groups = new ArrayList<>();

        for (int group = 0; group < 3; group++) {
            groups.add(group);
        }

        Collections.shuffle(groups, random);

        int[] result = new int[9];
        int position = 0;

        for (int group : groups) {
            List<Integer> offsets = new ArrayList<>();

            for (int offset = 0; offset < 3; offset++) {
                offsets.add(offset);
            }

            Collections.shuffle(offsets, random);

            for (int offset : offsets) {
                result[position++] = group * 3 + offset;
            }
        }

        return result;
    }
}
# output geography, infrastructure, transit files for a grid world


# for example for 1024*768, output a grid like this
# +-----+-----+-----+-----+
# |     |     |     |     |
# +-----+-----+-----+-----+
# |     |     |     |     |
# +-----+-----+-----+-----+
# |     |     |     |     |
# +-----+-----+-----+-----+

# Rows are named as A, B, C ...
# Cols are named as 1, 2, 3 ...

# pre-config
width = 1024
height = 768

row = 8#4
col = 10#5

row_gap = int(height / (row - 1))
col_gap = int(width / (col - 1))

with open("output/geography", "w") as geo_output:
    for i in range(0, row):
        for j in range(0, col):
            geo_output.write(chr(65 + i) + str(j + 1) + "," +
                             str(int(col_gap * j)) + "," + str(int(row_gap * i)) + "\n")

with open("output/infrastructure", "w") as infra_output:
    for i in range(0, row):
        for j in range(0, col):
            if j + 1 < col:
                infra_output.write(chr(65 + i) + str(j + 1) +
                                   "," + chr(65 + i) + str(j + 2) + "\n")
            if i + 1 < row:
                infra_output.write(chr(65 + i) + str(j + 1) +
                                   "," + chr(66 + i) + str(j + 1) + "\n")
with open("output/transit", "w") as transit_output:
    nodes = []
    for i in range(0, row):
        for j in range(0, col):
            nodes.append(chr(65+i)+str(j+1))

    for i in range(0, len(nodes)):
        for j in range(i, len(nodes)):
            transit_output.write(nodes[i]+","+nodes[j]+",500\n")
    # for i in range(0, row):
    #     for j in range(0, col):
    #         for  _i in range(i+1, row):
    #             for  _j in range(j+1, col):
    #                 transit_output.write(chr(65+i)+str(j+1)+","+chr(65+_i)+str(_j+1)+",500\n")
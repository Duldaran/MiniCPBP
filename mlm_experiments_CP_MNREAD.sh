#!/bin/bash
#SBATCH --time=10:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=1
#SBATCH --mem=12G



module load java/21.0.1
mkdir -p cp_gen

java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_v0_noBP 1.2 5000 cp_gen 15 MNREAD